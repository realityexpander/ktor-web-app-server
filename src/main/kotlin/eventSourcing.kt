import CustomerApplicationCommand.*
import CustomerApplicationEvent.*
import CustomerApplicationEvent.Approved
import CustomerApplicationEvent.Rejected
import CustomerApplicationState.*
import arrow.core.Either
//import arrow.core.computations.either
import arrow.core.raise.*
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ConcurrentHashMap


//----- The Core Design Elements

@JvmInline
value class Guid(val uuid: String = UUID.randomUUID().toString())

// -- Events
interface Event {
    val metadata: Metadata
}

data class Metadata(
    val correlationId: Guid,
    val sequence: Int = 0,
    val timestampMillis: Long = Instant.now().toEpochMilli()
) {
    val actionDate: LocalDate = LocalDate.ofInstant(
        Instant.ofEpochMilli(timestampMillis),
        ZoneId.of("America/Mexico_City")
    )
}

// -- State Models
interface State {
    val correlationId: Guid
}

// -- Commands
interface Command {
    fun correlationId(): Guid
}

interface CommandContext // to be used to pass around stuff like database connection pool, application config, etc

interface DomainError
data class ErrorMessage(val msg: String) : DomainError

// -- Command Handlers
typealias CmdResult<TEvent> = Either<DomainError, List<TEvent>> //cmd result is either an error or list of events

typealias CmdHandler<TCommand, TEvent> = (TCommand) -> CmdResult<TEvent>  //cmd handler takes a cmd and gives back cmd result

// -- Given a state and command, process and get a list of events
typealias ExecuteCommandFn<TState, TCommand, TCommandCtx, TEvent> = (TState, TCommand, TCommandCtx) -> CmdResult<TEvent>

fun <TState : State, TCommand : Command, TCommandCtx : CommandContext, TEvent : Event> executeCommand(
    state: TState,
    cmd: TCommand,
    cmdCtx: TCommandCtx,
    block: ExecuteCommandFn<TState, TCommand, TCommandCtx, TEvent>
): CmdResult<TEvent> = block(state, cmd, cmdCtx)

// -- Apply an event to a state and get the new state
typealias ApplyEventFn<TState, TEvent> = (TState?, TEvent) -> TState?

fun <TState : State, TEvent : Event> applyEvent(
    state: TState?,
    event: TEvent,
    block: ApplyEventFn<TState, TEvent>
): TState? =
    block(state, event)

fun <TState : State, TEvent : Event> applyEvents(
    state: TState?,
    events: List<TEvent>,
    block: ApplyEventFn<TState, TEvent>
): TState? = if(events.isEmpty()) state else events.fold(state, block)
//    events.fold(state, block)

// -- Aggregates
interface Aggregate<
        TState : State?,
        TCommand : Command,
        TCommandCtx : CommandContext,
        TEvent : Event
        > {
    val initialState: TState?
    fun execute(state: TState?, command: TCommand, commandContext: TCommandCtx): CmdResult<TEvent>
    fun apply(st: TState?, ev: TEvent): TState?
    fun apply(st: TState?, evs: List<TEvent>): TState?
}

//------------------------------------------------------------------

//Application Data
data class CustomerApplication(
    val customerName: String,
    val customerAddress: String,
    val customerPhone: String,
    val customerEmail: String,
    val appliedOn: LocalDate
)

enum class ApplicationStatus {
    New,
    UnderReview,
    NeedsMoreInfo,
    Approved,
    Rejected
}

//------------------------------------------------------------------

//Command Context
data class DefaultCmdContext(
    val nothing: String = "no_op_command"
) : CommandContext

//Commands
sealed class CustomerApplicationCommand : Command {
    abstract val customerApplicationId: Guid
    override fun correlationId() = customerApplicationId

    data class CreateApplication(
        override val customerApplicationId: Guid,
        val customerName: String,
        val customerAddress: String,
        val customerPhone: String,
        val customerEmail: String
    ) : CustomerApplicationCommand()

    data class SubmitApplication(
        override val customerApplicationId: Guid,
        val customerName: String,
        val customerAddress: String,
        val customerPhone: String,
        val customerEmail: String
    ) : CustomerApplicationCommand()

    data class UpdateDetails(
        override val customerApplicationId: Guid,
        val customerName: String,
        val customerAddress: String,
        val customerPhone: String,
        val customerEmail: String
    ) : CustomerApplicationCommand()

    data class RequestMoreInfo(
        override val customerApplicationId: Guid,
        val reason: String,
        val sentBackBy: String
    ) : CustomerApplicationCommand()

    data class ApproveApplication(
        override val customerApplicationId: Guid,
        val approvingUser: String
    ) : CustomerApplicationCommand()

    data class RejectApplication(
        override val customerApplicationId: Guid,
        val rejectingUser: String
    ) : CustomerApplicationCommand()
}

//Events
sealed class CustomerApplicationEvent : Event {
//    abstract override val metadata: Metadata

    data class Created(
        override val metadata: Metadata,
        val customerName: String,
        val customerAddress: String,
        val customerPhone: String,
        val customerEmail: String,
    ) : CustomerApplicationEvent()

    data class Submitted(
        override val metadata: Metadata,
        val customerName: String,
        val customerAddress: String,
        val customerPhone: String,
        val customerEmail: String,
    ) : CustomerApplicationEvent()

    data class Updated(
        override val metadata: Metadata,
        val customerName: String,
        val customerAddress: String,
        val customerPhone: String,
        val customerEmail: String
    ) : CustomerApplicationEvent()

    data class MoreInfoRequested(
        override val metadata: Metadata,
        val reason: String,
        val sentBackBy: String
    ) : CustomerApplicationEvent()

    data class Approved(
        override val metadata: Metadata,
        val approvedBy: String
    ) : CustomerApplicationEvent()

    data class Rejected(
        override val metadata: Metadata,
        val rejectedBy: String
    ) : CustomerApplicationEvent()
}

//States
sealed class CustomerApplicationState : State {
    //    abstract override val correlationId: Guid // needed here?
    abstract val applicationStatus: ApplicationStatus

    data class New(
        override val correlationId: Guid,
        override val applicationStatus: ApplicationStatus = ApplicationStatus.New
    ) : CustomerApplicationState()

    data class UnderReview(
        override val correlationId: Guid,
        val applicationData: CustomerApplication,
        override val applicationStatus: ApplicationStatus = ApplicationStatus.UnderReview
    ) : CustomerApplicationState()

    data class NeedsMoreInfo(
        override val correlationId: Guid,
        val applicationData: CustomerApplication,
        val sentBackBy: String,
        val sentBackOn: LocalDate,
        override val applicationStatus: ApplicationStatus = ApplicationStatus.NeedsMoreInfo
    ) : CustomerApplicationState()

    data class Approved(
        override val correlationId: Guid,
        val applicationData: CustomerApplication,
        val approvedBy: String,
        val approvedOn: LocalDate,
        override val applicationStatus: ApplicationStatus = ApplicationStatus.Approved
    ) : CustomerApplicationState()

    data class Rejected(
        override val correlationId: Guid,
        val applicationData: CustomerApplication,
        val rejectedBy: String,
        val rejectedOn: LocalDate,
        override val applicationStatus: ApplicationStatus = ApplicationStatus.Rejected
    ) : CustomerApplicationState()
}

//------------------------------------------------------------------------
fun handleCustomerApplicationCommand(
    st: CustomerApplicationState?,
    cmd: CustomerApplicationCommand,
    ctx: CommandContext
): CmdResult<CustomerApplicationEvent> =
    when (cmd) {
        is CreateApplication -> {
            listOf(
                Created(
                    Metadata(cmd.customerApplicationId),
                    cmd.customerName,
                    cmd.customerAddress,
                    cmd.customerPhone,
                    cmd.customerEmail
                )
            ).right()
        }

        is SubmitApplication ->
            either {
                listOf(
                    Submitted(
                        Metadata(cmd.customerApplicationId),
                        cmd.customerName,
                        cmd.customerAddress,
                        cmd.customerPhone,
                        cmd.customerEmail
                    )
                )
            }

        is UpdateDetails ->
            either {
                listOf(
                    Updated(
                        Metadata(cmd.customerApplicationId),
                        cmd.customerName,
                        cmd.customerAddress,
                        cmd.customerPhone,
                        cmd.customerEmail
                    )
                )
            }

        is RequestMoreInfo ->
            either {
                if (st !is UnderReview) {
                    return ErrorMessage("Application illegal state error").left()
                }

                listOf(
                    MoreInfoRequested(
                        Metadata(cmd.customerApplicationId),
                        cmd.reason,
                        cmd.sentBackBy
                    )
                )
            }

        is ApproveApplication ->
            either {
                if (st !is UnderReview) {
                    return ErrorMessage("Application illegal state error").left()
                }

                // TODO: check if application has all the info needed
                return ErrorMessage("Application missing info error").left()  // simulate error

                listOf(
                    Approved(
                        Metadata(cmd.customerApplicationId),
                        cmd.approvingUser
                    )
                )
            }

        is RejectApplication ->
            either {
                listOf(
                    Rejected(
                        Metadata(cmd.customerApplicationId),
                        cmd.rejectingUser
                    )
                )
            }

    }


fun executeCustomerApplicationCmd(
    st: CustomerApplicationState,
    cmd: CustomerApplicationCommand,
    ctx: CommandContext = DefaultCmdContext()
): CmdResult<CustomerApplicationEvent> =
    executeCommand(st, cmd, ctx, ::handleCustomerApplicationCommand)

//------------------------------------------------------------------------
fun applyCustomerApplicationEvent(
    st: CustomerApplicationState?,
    evt: CustomerApplicationEvent
): CustomerApplicationState? =
    when {
        st == null && evt is Created ->
            New(
                evt.metadata.correlationId
            )

        st is New && evt is Submitted ->
            UnderReview(
                evt.metadata.correlationId,
                CustomerApplication(
                    evt.customerName,
                    evt.customerAddress,
                    evt.customerPhone,
                    evt.customerEmail,
                    evt.metadata.actionDate
                )
            )

        st is UnderReview && evt is Updated ->
            UnderReview(
                evt.metadata.correlationId,
                CustomerApplication(
                    evt.customerName,
                    evt.customerAddress,
                    evt.customerPhone,
                    evt.customerEmail,
                    evt.metadata.actionDate
                )
            )

        st is UnderReview && evt is MoreInfoRequested ->
            NeedsMoreInfo(
                evt.metadata.correlationId,
                st.applicationData,
                evt.sentBackBy,
                evt.metadata.actionDate
            )

        st is UnderReview && evt is Approved ->
            CustomerApplicationState.Approved(
                evt.metadata.correlationId,
                st.applicationData,
                evt.approvedBy,
                evt.metadata.actionDate
            )

        st is UnderReview && evt is Rejected ->
            CustomerApplicationState.Rejected(
                evt.metadata.correlationId,
                st.applicationData,
                evt.rejectedBy,
                evt.metadata.actionDate
            )

        else -> st
    }

//------------------------------------------------------------------------
//Simulate the database

interface EventStream : Iterable<Event> {
    fun <TEvent : Event> append(newEvents: List<TEvent>): EventStream
}

class CustomerApplicationEvents(
    val events: List<Event> = emptyList()
) : EventStream {
    override fun <TEvent : Event> append(newEvents: List<TEvent>): CustomerApplicationEvents =
        CustomerApplicationEvents(events + newEvents)

    override fun iterator(): Iterator<Event> = events.iterator()
}

interface EventStore {
    fun loadEventStream(correlationId: Guid): EventStream
    fun <TEvent : Event> store(correlationId: Guid, events: List<TEvent>): DomainError?

    fun dump()
}

class CustomerApplicationsEventDatabase : EventStore {
    private val applications = ConcurrentHashMap<Guid, CustomerApplicationEvents>() // simulate the database

    override fun loadEventStream(correlationId: Guid): CustomerApplicationEvents =
        applications.getOrPut(correlationId) {
            CustomerApplicationEvents()
        }

    override fun <TEvent : Event> store(
        correlationId: Guid,
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        newEvents: List<TEvent>
    ): DomainError? =
        loadEventStream(correlationId).let { events ->
            applications[correlationId] = events.append(newEvents)
            null
        }

    override fun dump() {
        applications.forEach { (correlationId, events) ->
            println("CorrelationId: $correlationId")
            events.forEach { println("  $it") }
        }
    }
}

//------------------------------------------------------------------------

class CustomerApplicationAggregate(
    private val es: CustomerApplicationsEventDatabase
) : Aggregate<
        CustomerApplicationState,
        CustomerApplicationCommand,
        CommandContext,
        CustomerApplicationEvent
        > {
//    override val initialState = New(Guid("100"))
    override val initialState = null

    override fun apply(st: CustomerApplicationState?, ev: CustomerApplicationEvent): CustomerApplicationState? =
        applyEvent(st, ev, ::applyCustomerApplicationEvent)

    override fun apply(st: CustomerApplicationState?, evs: List<CustomerApplicationEvent>): CustomerApplicationState? =
        applyEvents(st, evs, ::applyCustomerApplicationEvent)

    override fun execute(
        state: CustomerApplicationState?,
        command: CustomerApplicationCommand,
        commandContext: CommandContext
    ): CmdResult<CustomerApplicationEvent> {
        val eventStream = es.loadEventStream(command.correlationId())
        val events = eventStream.events as List<CustomerApplicationEvent>
        val currentState = apply(initialState, events)

//        val result =
//            executeCommand(
//                currentState, cmd, DefaultCmdContext(), ::executeCustomerApplicationCmd
//            )
//            .flatMap { evts ->
//                es.store(cmd.correlationId(), evts)
//                evts.right()
//            }

        val result =
            handleCustomerApplicationCommand(currentState, command, commandContext)
            .flatMap { evts ->
                es.store(command.correlationId(), evts)
                evts.right()
            }

        if (result.isLeft()) {
            println("Error: ${result.leftOrNull()}")
        }

        return result
    }
}

//------------------------------------------------------------------------
fun main() {
    val db = CustomerApplicationsEventDatabase()
    val cmdCtx = DefaultCmdContext()
    val agg = CustomerApplicationAggregate(db)

    val customerApplication = New(Guid("100"))

    print("0 System state before creating the application -> ")
    val correlationId = Guid()
    val currentState0 = agg.apply(
        customerApplication,
        db.loadEventStream(correlationId).events as List<CustomerApplicationEvent>
    )
    println(currentState0)

    val cmd0 = CreateApplication(
        correlationId,
        "John Doe",
        "Vadodara",
        "+91-9988776655",
        "john@doe.com"
    )
    agg.execute(customerApplication, cmd0, cmdCtx)

    print("1 System state after creating the application -> ")
    //Finding the current state of an application
    val currentState1 = agg.apply(
        customerApplication,
        db.loadEventStream(correlationId).events as List<CustomerApplicationEvent>
    )
    println(currentState1)
    println()

    val cmd1 = SubmitApplication(
        correlationId,
        "John Doe",
        "Vadodara",
        "+91-9988776655",
        "john@doe.com"
    )
    agg.execute(customerApplication, cmd1, cmdCtx)

    print("2 System state after submitting the application -> ")
    //Finding the current state of an application
    val currentState2 = agg.apply(
        customerApplication,
        db.loadEventStream(correlationId).events as List<CustomerApplicationEvent>
    )
    println(currentState2)
    println()


    val cmd2 = ApproveApplication(correlationId, "Jane Doe")
    val result = agg.execute(customerApplication, cmd2, cmdCtx)
    if (result.isLeft()) {
        //Error handling
        val cmd = RequestMoreInfo(correlationId, "Please provide more information", "Bilbo Baggins")
        agg.execute(customerApplication, cmd, cmdCtx)
    }

    print("3 System state after attempting to approve the application -> ")
    //Finding the current state of an application
    val currentState3 = agg.apply(
        customerApplication,
        db.loadEventStream(correlationId).events as List<CustomerApplicationEvent>
    )
    println(currentState3)

    db.dump()
}