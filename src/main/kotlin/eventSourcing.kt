import CustomerApplicationCmd.*
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

inline class Guid(val uuidString: String = UUID.randomUUID().toString())

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
typealias ApplyEventFn<TState, TEvent> = (TState, TEvent) -> TState

fun <TState : State, TEvent : Event> applyEvent(
    state: TState,
    event: TEvent,
    block: ApplyEventFn<TState, TEvent>
): TState =
    block(state, event)

fun <TState : State, TEvent : Event> applyEvents(
    state: TState,
    events: List<TEvent>,
    block: ApplyEventFn<TState, TEvent>
): TState =
    events.fold(state, block)

// -- Aggregates
interface Aggregate<
        TState : State,
        TCommand : Command,
        TCommandCtx : CommandContext,
        TEvent : Event
        > {
    val initialState: TState
    fun execute(st: TState, cmd: TCommand, cmdCtx: TCommandCtx): CmdResult<TEvent>
    fun apply(st: TState, ev: TEvent): TState
    fun apply(st: TState, evs: List<TEvent>): TState
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
sealed class CustomerApplicationCmd : Command {
    abstract val customerApplicationId: Guid
    override fun correlationId() = customerApplicationId

    data class SubmitApplication(
        override val customerApplicationId: Guid,
        val customerName: String,
        val customerAddress: String,
        val customerPhone: String,
        val customerEmail: String
    ) : CustomerApplicationCmd()

    data class UpdateDetails(
        override val customerApplicationId: Guid,
        val customerName: String,
        val customerAddress: String,
        val customerPhone: String,
        val customerEmail: String
    ) : CustomerApplicationCmd()

    data class RequestMoreInfo(
        override val customerApplicationId: Guid,
        val reason: String,
        val sentBackBy: String
    ) : CustomerApplicationCmd()

    data class ApproveApplication(
        override val customerApplicationId: Guid,
        val approvingUser: String
    ) : CustomerApplicationCmd()

    data class RejectApplication(
        override val customerApplicationId: Guid,
        val rejectingUser: String
    ) : CustomerApplicationCmd()
}

//Events
sealed class CustomerApplicationEvent : Event {
//    abstract override val metadata: Metadata

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
fun handleCustomerApplicationCmd(
    st: CustomerApplicationState,
    cmd: CustomerApplicationCmd,
    ctx: CommandContext
): CmdResult<CustomerApplicationEvent> =
    when (cmd) {
        is SubmitApplication ->
            either {
                listOf(
                    Submitted(
                        Metadata(Guid(cmd.customerApplicationId.toString())),
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
                        Metadata(Guid(cmd.customerApplicationId.toString())),
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
                        Metadata(Guid(cmd.customerApplicationId.toString())),
                        cmd.reason,
                        cmd.sentBackBy
                    )
                )
            }

        is ApproveApplication ->
            either {
                if (st !is UnderReview) {
                    return ErrorMessage("Application approval error").left()
                }

                return ErrorMessage("Application missing info error").left()

                listOf(
                    Approved(
                        Metadata(Guid(cmd.customerApplicationId.toString())),
                        cmd.approvingUser
                    )
                )
            }

        is RejectApplication ->
            either {
                listOf(
                    Rejected(
                        Metadata(Guid(cmd.customerApplicationId.toString())),
                        cmd.rejectingUser
                    )
                )
            }
    }


fun executeCustomerApplicationCmd(
    st: CustomerApplicationState,
    cmd: CustomerApplicationCmd,
    ctx: CommandContext
): CmdResult<CustomerApplicationEvent> =
//    when {
//        st is New && cmd is SubmitApplication ->
//            executeCommand(st, cmd, DefaultCmdContext(), ::handleCustomerApplicationCmd)
//
//        st is UnderReview && cmd is UpdateDetails ->
//            executeCommand(st, cmd, DefaultCmdContext(), ::handleCustomerApplicationCmd)
//
//        st is NeedsMoreInfo && cmd is UpdateDetails ->
//            executeCommand(st, cmd, DefaultCmdContext(), ::handleCustomerApplicationCmd)
//
//        st is UnderReview && cmd is ApproveApplication ->
//            executeCommand(st, cmd, DefaultCmdContext(), ::handleCustomerApplicationCmd)
//
//        st is UnderReview && cmd is RejectApplication ->
//            executeCommand(st, cmd, DefaultCmdContext(), ::handleCustomerApplicationCmd)
//
//        else ->
//            ErrorMessage("action not allowed").left()
//    }
    executeCommand(st, cmd, DefaultCmdContext(), ::handleCustomerApplicationCmd)

//------------------------------------------------------------------------
fun applyCustomerApplicationEvent(
    st: CustomerApplicationState,
    evt: CustomerApplicationEvent
): CustomerApplicationState =
    when {
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
        CustomerApplicationCmd,
        CommandContext,
        CustomerApplicationEvent
        > {
    override val initialState = New(Guid("100"))

    override fun apply(st: CustomerApplicationState, ev: CustomerApplicationEvent): CustomerApplicationState =
        applyEvent(st, ev, ::applyCustomerApplicationEvent)

    override fun apply(st: CustomerApplicationState, evs: List<CustomerApplicationEvent>): CustomerApplicationState =
        applyEvents(st, evs, ::applyCustomerApplicationEvent)

    override fun execute(
        st: CustomerApplicationState,
        cmd: CustomerApplicationCmd,
        cmdCtx: CommandContext
    ): CmdResult<CustomerApplicationEvent> {
        val eventStream = es.loadEventStream(cmd.correlationId())
        val events = eventStream.events as List<CustomerApplicationEvent>
        val currentState = apply(initialState, events)

        val result =
            executeCommand(
                currentState, cmd, DefaultCmdContext(), ::executeCustomerApplicationCmd
            ).flatMap { evts ->
                es.store(cmd.correlationId(), evts)
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

    val customer = New(Guid("100"))

    print("1 System state before submitting the application -> ")
    val correlationId = Guid()
    val currentState1 = agg.apply(
        customer,
        db.loadEventStream(correlationId).events as List<CustomerApplicationEvent>
    )
    println(currentState1)

    val cmd1 = SubmitApplication(
        correlationId,
        "John Doe",
        "Vadodara",
        "+91-9988776655",
        "john@doe.com"
    )
    agg.execute(customer, cmd1, cmdCtx)

    print("2 System state after submitting the application -> ")
    //Finding the current state of an application
    val currentState2 = agg.apply(
        customer,
        db.loadEventStream(correlationId).events as List<CustomerApplicationEvent>
    )
    println(currentState2)
    println()


    val cmd2 = ApproveApplication(correlationId, "Jane Doe")
    val result = agg.execute(customer, cmd2, cmdCtx)
    if (result.isLeft()) {
        //Error handling
        val cmd = RequestMoreInfo(correlationId, "Please provide more information", "Bilbo Baggins")
        agg.execute(customer, cmd, cmdCtx)
    }

    print("3 System state after attempting to approve the application -> ")
    //Finding the current state of an application
    val currentState3 = agg.apply(
        customer,
        db.loadEventStream(correlationId).events as List<CustomerApplicationEvent>
    )
    println(currentState3)

    db.dump()
}