package domain.account.data;

import org.elegantobjects.jpages.LibraryApp.common.util.Result;
import org.elegantobjects.jpages.LibraryApp.common.util.uuid2.UUID2;
import org.elegantobjects.jpages.LibraryApp.domain.account.Account;
import org.elegantobjects.jpages.LibraryApp.domain.book.Book;
import org.elegantobjects.jpages.LibraryApp.domain.common.data.Model;
import org.elegantobjects.jpages.LibraryApp.domain.common.data.info.DomainInfo;
import org.elegantobjects.jpages.LibraryApp.domain.user.User;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.UUID;

/**
AccountInfo contains data about a single User's account status in the LibraryApp system.<br>
<ul>
 <li>Status of Account (active, inactive, suspended, etc.)</li>
 <li>Current Fine Amount
 <li>Max books allowed to be checked out
</ul>
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

@SuppressWarnings("CommentedOutCode")
public class AccountInfo extends DomainInfo
        implements
        Model.ToDomainInfo<AccountInfo>
{
    public final UUID2<User> userId;           // This Account UUID matches the UUID of the UUID2<User> for this Account.
    public final String name;
    public final AccountStatus accountStatus;  // status of Account (active, inactive, suspended, etc.)
    public final int currentFinePennies;       // current fine amount in pennies
    public final int maxAcceptedBooks;         // max Books allowed to be checked out by this User
    public final int maxFinePennies;           // max fine amount allowed before account is suspended

    // Showing object can use internal ways to track its own data that will not be directly exposed to the outside world.
    // ie: we could have used a "Log" role here, but instead we just use a HashMap. // todo should this be a subclassed Log Role instead?
    final private HashMap<Long, AccountAuditLogItem> timeStampToAccountAuditLogItemMap; // timestamp_ms -> AccountAuditLogItem

    // LEAVE for future use
    // final int maxDays;               // max number of days a book can be checked out
    // final int maxRenewals;           // max number of renewals (per book)
    // final int maxRenewalDays;        // max number days for each renewal (per book)
    // final int maxFineAmountPennies;  // max dollar amount of all fines allowed before account is suspended
    // final int maxFineDays;           // max number of days to pay fine before account is suspended

    public
    AccountInfo(
        @NotNull UUID2<Account> id,  // UUID should match User's UUID
        @NotNull String name,
        @NotNull AccountStatus accountStatus,
        int currentFinePennies,
        int maxAcceptedBooks,
        int maxFinePennies,
        @NotNull HashMap<Long, AccountAuditLogItem> timeStampToAccountAuditLogItemMap
    ) {
        super(id);
        this.userId = new UUID2<>(id); // set the Accounts' User UUID to match the Account's UUID
        this.name = name;
        this.accountStatus = accountStatus;
        this.currentFinePennies = currentFinePennies;
        this.maxAcceptedBooks = maxAcceptedBooks;
        this.maxFinePennies = maxFinePennies;
        this.timeStampToAccountAuditLogItemMap = timeStampToAccountAuditLogItemMap;
    }
    public
    AccountInfo(@NotNull UUID2<Account> id, @NotNull String name) { // sensible defaults
        this(
            id,
            name,
            AccountStatus.ACTIVE,
            0,
            5,
            1000,
            new HashMap<>()
        );
    }
    public
    AccountInfo(@NotNull AccountInfo accountInfo) {
        this(
            accountInfo.id(),
            accountInfo.name,
            accountInfo.accountStatus,
            accountInfo.currentFinePennies,
            accountInfo.maxAcceptedBooks,
            accountInfo.maxFinePennies,
            accountInfo.timeStampToAccountAuditLogItemMap
        );
    }
    public
    AccountInfo(@NotNull UUID uuid, @NotNull String name) {
        this(new UUID2<Account>(uuid, Account.class), name);
    }
    public
    AccountInfo(@NotNull String id, @NotNull String name) {
        this(UUID.fromString(id), name);
    }

    enum AccountStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        CLOSED
    }

    static class AccountAuditLogItem {
        final Long timeStampLongMillis;
        final String operation;
        final HashMap<String, String> entries;  // keyString -> valueString

        public
        AccountAuditLogItem(
            @NotNull Long timeStampMillis,
            @NotNull String operation,
            @NotNull HashMap<String, String> kvHashMap
        ) {
            this.timeStampLongMillis = timeStampMillis;
            this.operation = operation;
            this.entries = new HashMap<>();
            this.entries.putAll(kvHashMap);
        }
    }

    ///////////////////////////////
    // Published Simple Getters  //
    ///////////////////////////////

    @Override @SuppressWarnings("unchecked")
    public UUID2<Account> id() {
        return (UUID2<Account>) super.id();
    }
    public String name() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.toPrettyJson();
    }

    /////////////////////////////////////////////
    // Published Role Business Logic Methods //
    /////////////////////////////////////////////

    public Result<AccountInfo> activateAccountByStaff(String reason, String staffMemberName) {
        if (reason == null || reason.isEmpty())
            return new Result.Failure<>(new IllegalArgumentException("reason is null or empty"));
        if (staffMemberName == null || staffMemberName.isEmpty())
            return new Result.Failure<>(new IllegalArgumentException("staffMemberName is null or empty"));

        addAuditLogEntry("activateAccountByStaff", "reason", reason, "staffMemberName", staffMemberName);

        return new Result.Success<>(
            new AccountInfo(
                this.id(),
                this.name,
                AccountStatus.ACTIVE,
                this.currentFinePennies,
                this.maxAcceptedBooks,
                this.maxFinePennies,
                this.timeStampToAccountAuditLogItemMap
        ));
    }
    public Result<AccountInfo> deactivateAccountByStaff(String reason, String staffMemberName) {
        if (reason == null || reason.isEmpty())
            return new Result.Failure<>(new IllegalArgumentException("reason is null or empty"));
        if (staffMemberName == null || staffMemberName.isEmpty())
            return new Result.Failure<>(new IllegalArgumentException("staffMemberName is null or empty"));

        addAuditLogEntry("deactivateAccountByStaff", "reason", reason, "staffMemberName", staffMemberName);

        return new Result.Success<>(
            new AccountInfo(
                this.id(),
                this.name,
                AccountStatus.INACTIVE,
                this.currentFinePennies,
                this.maxAcceptedBooks,
                this.maxFinePennies,
                this.timeStampToAccountAuditLogItemMap
        ));
    }
    public Result<AccountInfo> suspendAccountByStaff(String reason, String staffMemberName) {
        if (reason == null || reason.isEmpty())
            return new Result.Failure<>(new IllegalArgumentException("reason is null or empty"));
        if (staffMemberName == null || staffMemberName.isEmpty())
            return new Result.Failure<>(new IllegalArgumentException("staffMemberName is null or empty"));

        addAuditLogEntry("suspendAccountByStaff", "reason", reason, "staffMemberName", staffMemberName);

        return new Result.Success<>(
            new AccountInfo(this.id(),
                this.name,
                AccountStatus.SUSPENDED,
                this.currentFinePennies,
                this.maxAcceptedBooks,
                this.maxFinePennies,
                this.timeStampToAccountAuditLogItemMap
        ));
    }
    public Result<AccountInfo> closeAccountByStaff(String reason, String staffMemberName) {
        if (reason == null || reason.isEmpty())
            return new Result.Failure<>(new IllegalArgumentException("reason is null or empty"));
        if (staffMemberName == null || staffMemberName.isEmpty())
            return new Result.Failure<>(new IllegalArgumentException("staffMemberName is null or empty"));

        addAuditLogEntry("closeAccountByStaff", "reason", reason, "staffMemberName", staffMemberName);

        return new Result.Success<>(
            new AccountInfo(this.id(),
                this.name,
                AccountStatus.CLOSED,
                this.currentFinePennies,
                this.maxAcceptedBooks,
                this.maxFinePennies,
                this.timeStampToAccountAuditLogItemMap
        ));
    }

    public Result<AccountInfo> addFineForBook(int fineAmountPennies, UUID2<Book> bookId) {
        if (fineAmountPennies < 0)
            return new Result.Failure<>(new IllegalArgumentException("fineAmountPennies is negative"));
        if (bookId == null)
            return new Result.Failure<>(new IllegalArgumentException("book is null"));

        addAuditLogEntry("addFine", "fineAmountPennies", fineAmountPennies, "bookId", bookId);

        AccountStatus updatedAccountStatus = this.accountStatus;
        if (calculateAccountStatus() != this.accountStatus)
            updatedAccountStatus = calculateAccountStatus();

        return new Result.Success<>(
            new AccountInfo(this.id(),
                this.name,
                updatedAccountStatus,
                this.currentFinePennies + fineAmountPennies,
                this.maxAcceptedBooks,
                this.maxFinePennies,
                this.timeStampToAccountAuditLogItemMap
        ));
    }
    public Result<AccountInfo> payFine(int fineAmountPennies) {
        if (fineAmountPennies < 0)
            return new Result.Failure<>(new IllegalArgumentException("fineAmountPennies is negative"));

        addAuditLogEntry("payFine", fineAmountPennies);

        AccountStatus updatedAccountStatus = this.accountStatus;
        if (calculateAccountStatus() != this.accountStatus)
            updatedAccountStatus = calculateAccountStatus();

        return new Result.Success<>(
            new AccountInfo(this.id(),
                this.name,
                updatedAccountStatus,
                this.currentFinePennies - fineAmountPennies,
                this.maxAcceptedBooks,
                this.maxFinePennies,
                this.timeStampToAccountAuditLogItemMap
        ));

    }
    public Result<AccountInfo> adjustFineByStaff(int newCurrentFineAmount, String reason, String staffMemberName) { // todo make staffMemberName a User.Staff object
        if (newCurrentFineAmount < 0)
            return new Result.Failure<>(new IllegalArgumentException("newCurrentFineAmount is negative"));

        addAuditLogEntry("adjustFineByStaff", "reason", reason, "staffMember", staffMemberName);

        AccountStatus updatedAccountStatus = this.accountStatus;
        if (calculateAccountStatus() != this.accountStatus)
            updatedAccountStatus = calculateAccountStatus();

        return new Result.Success<>(
            new AccountInfo(this.id(),
                this.name,
                updatedAccountStatus,
                newCurrentFineAmount,
                this.maxAcceptedBooks,
                this.maxFinePennies,
                this.timeStampToAccountAuditLogItemMap
        ));
    }

    public Result<AccountInfo> changeMaxBooksByStaff(int maxBooks, String reason, String staffMemberName) { // todo make staffMemberName a User.Staff object
        if (maxBooks < 0)
            return new Result.Failure<>(new IllegalArgumentException("maxBooks is negative"));

        addAuditLogEntry("changeMaxBooksByStaff", "reason", reason, "staffMember", staffMemberName);

        return new Result.Success<>(
            new AccountInfo(this.id(),
                this.name,
                this.accountStatus,
                this.currentFinePennies,
                maxBooks,
                this.maxFinePennies,
                this.timeStampToAccountAuditLogItemMap));
    }
    public Result<AccountInfo> changeMaxFineByStaff(int maxFine, String reason, String staffMemberName) { // todo make staffMemberName a User.Staff object
        if (maxFine < 0)
            return new Result.Failure<>(new IllegalArgumentException("maxFine is negative"));

        addAuditLogEntry("changeMaxFineByStaff", "reason", reason, "staffMember", staffMemberName);

        return new Result.Success<>(
            new AccountInfo(this.id(),
                this.name,
                this.accountStatus,
                this.currentFinePennies,
                this.maxAcceptedBooks,
                maxFine,
                this.timeStampToAccountAuditLogItemMap));
    }

    /////////////////////////////////////////
    // Published Role Reporting Methods  //
    /////////////////////////////////////////

    public int calculateFineAmountPennies() {
        return this.currentFinePennies;
    }

    public String[] getAuditLogStrings() {
        return this.timeStampToAccountAuditLogItemMap
                .entrySet()
                .stream()
                .map(entry ->  // Convert audit log to array of `timestamp:{action:"data"}` strings
                        convertTimeStampLongMillisToIsoDateTimeString(entry.getKey()) +
                        ": " +
                        entry.getValue()
                )
                .toArray(String[]::new);
    }

    private @NotNull String convertTimeStampLongMillisToIsoDateTimeString(long timeStampMillis) {
        return DateTimeFormatter.ISO_DATE_TIME.format(
            Instant.ofEpochMilli(timeStampMillis)
        );
    }

    /////////////////////////////////
    // Published Helper Methods    //
    /////////////////////////////////

    public boolean isAccountActive() {
        return this.accountStatus == AccountStatus.ACTIVE;
    }
    public boolean isAccountInactive() {
        return this.accountStatus == AccountStatus.INACTIVE;
    }
    public boolean isAccountSuspended() {
        return this.accountStatus == AccountStatus.SUSPENDED;
    }
    public boolean isAccountClosed() {
        return this.accountStatus == AccountStatus.CLOSED;
    }
    public boolean isAccountInGoodStanding() {
        return this.accountStatus == AccountStatus.ACTIVE
            && !this.isMaxFineExceeded();
    }
    public boolean isAccountInBadStanding() {
        return this.accountStatus == AccountStatus.SUSPENDED
            || this.accountStatus == AccountStatus.CLOSED
            || this.accountStatus == AccountStatus.INACTIVE
            || this.isMaxFineExceeded();
    }
    public boolean isAccountInGoodStandingWithNoFines()  {
        return this.accountStatus == AccountStatus.ACTIVE
                && hasNoFines();
    }
    public boolean isAccountInGoodStandingWithFines() {
        return this.accountStatus == AccountStatus.ACTIVE
                || this.accountStatus == AccountStatus.INACTIVE
                && this.currentFinePennies > 0
                && !this.isMaxFineExceeded();
    }
    public boolean isAccountInGoodStandingAndAbleToBorrowBooks(int numberOfBooksInPossession) {
        return this.accountStatus == AccountStatus.ACTIVE
            && !this.isMaxFineExceeded()
            && !this.hasReachedMaxAmountOfAcceptedLibraryBooks(numberOfBooksInPossession);
    }

    public boolean hasFines() {
        return this.currentFinePennies > 0;
    }
    public boolean hasNoFines() {
        return this.currentFinePennies <= 0;
    }
    public boolean isMaxFineExceeded() {
        return this.currentFinePennies >= this.maxFinePennies;
    }
    public boolean hasReachedMaxAmountOfAcceptedLibraryBooks(int numberOfBooksInPossession) {
        return numberOfBooksInPossession >= this.maxAcceptedBooks;
    }

    // todo - calculate fines based on time passed since book was due, etc.

    /////////////////////////////////////////
    // Published Testing Helper Methods    //
    /////////////////////////////////////////

    public void addTestAuditLogMessage(String message) {
        addAuditLogEntry("addTestAuditLogMessage", message);
    }

    //////////////////////////////
    // Private Helper Functions //
    //////////////////////////////

    public Result<AccountInfo> activateAccount() {
        // Note: this status will be overridden if User pays a fine or Library adds a fine
        return this.withAccountStatus(AccountStatus.ACTIVE);
    }
    public Result<AccountInfo> deactivateAccount() {
        return this.withAccountStatus(AccountStatus.INACTIVE);
    }
    public Result<AccountInfo> suspendAccount() {
        // Note: this status will be overridden if User pays a fine or Library adds a fine
        return this.withAccountStatus(AccountStatus.SUSPENDED);
    }
    public Result<AccountInfo> closeAccount() {
        return this.withAccountStatus(AccountStatus.CLOSED);
    }

    private AccountStatus calculateAccountStatus() {
        if (this.accountStatus == AccountStatus.CLOSED)
            return AccountStatus.CLOSED;

        if (this.accountStatus == AccountStatus.INACTIVE)
            return AccountStatus.INACTIVE;

        if (this.currentFinePennies > this.maxFinePennies) {
            return AccountStatus.SUSPENDED;
        }

        return AccountStatus.ACTIVE;
    }

    private void addAuditLogEntry(
        @NotNull Long timeStampMillis,
        @NotNull String operation,
        @NotNull String key1, @NotNull Object value1,
        @NotNull String key2, @NotNull Object value2
    ) {
        HashMap<String, String> keyValMap = new HashMap<>();
        keyValMap.put(key1, value1.toString());
        keyValMap.put(key2, value2.toString());

        timeStampToAccountAuditLogItemMap.put(
                timeStampMillis,
                new AccountAuditLogItem(timeStampMillis, operation, keyValMap)
        );
    }
    private void addAuditLogEntry(@NotNull Long timeStampMillis, @NotNull String operation, @NotNull Object value) {
        HashMap<String, String> keyValMap = new HashMap<>();
        keyValMap.put("value", value.toString());

        timeStampToAccountAuditLogItemMap.put(
                timeStampMillis,
                new AccountAuditLogItem(timeStampMillis, operation, keyValMap)
        );
    }
    private void addAuditLogEntry(@NotNull Long timeStampMillis, @NotNull String operation) {
        timeStampToAccountAuditLogItemMap.put(
                timeStampMillis,
                new AccountAuditLogItem(timeStampMillis, operation, new HashMap<>())
        );
    }
    private void addAuditLogEntry(@NotNull String operation, @NotNull String key1, @NotNull  Object value1, @NotNull String key2, @NotNull Object value2) {
        addAuditLogEntry(System.currentTimeMillis(), operation, key1, value1, key2, value2);
    }
    private void addAuditLogEntry(@NotNull String operation, @NotNull Object value) {
        addAuditLogEntry(System.currentTimeMillis(), operation, value);
    }
    private void addAuditLogEntry(@NotNull String operation) {
        addAuditLogEntry(System.currentTimeMillis(), operation);
    }

    private Result<AccountInfo> withName(@NotNull String newName) {
        if (newName.isEmpty())
            return new Result.Failure<>(new IllegalArgumentException("newName is null or empty"));

        return new Result.Success<>(new AccountInfo(this.id(), newName));
    }
    private  Result<AccountInfo> withAccountStatus(@NotNull AccountStatus newAccountStatus) {
        return new Result.Success<>(
            new AccountInfo(
                this.id(),
                this.name,
                newAccountStatus,
                this.currentFinePennies,
                this.maxAcceptedBooks,
                this.maxFinePennies,
                this.timeStampToAccountAuditLogItemMap
        ));
    }
    private Result<AccountInfo> withCurrentFineAmountPennies(int newCurrentFineAmountPennies) {
        if (newCurrentFineAmountPennies < 0)
            return new Result.Failure<>(new IllegalArgumentException("newCurrentFineAmountPennies is negative")); // todo - allow credits for Fines?

        return new Result.Success<>(
            new AccountInfo(
                this.id(),
                this.name,
                this.accountStatus,
                newCurrentFineAmountPennies,
                this.maxAcceptedBooks,
                this.maxFinePennies,
                this.timeStampToAccountAuditLogItemMap
        ));
    }
    private Result<AccountInfo> withMaxBooks(int newMaxBooks) {
        if (newMaxBooks < 0)
            return new Result.Failure<>(new IllegalArgumentException("newMaxBooks is negative"));

        return new Result.Success<>(
            new AccountInfo(
                this.id(),
                this.name,
                this.accountStatus,
                this.currentFinePennies,
                newMaxBooks,
                this.maxFinePennies,
                this.timeStampToAccountAuditLogItemMap
        ));
    }

    /////////////////////////////////
    // ToDomainInfo implementation //
    /////////////////////////////////

    // note: currently no DB or API for UserInfo (so no .ToEntity() or .ToDTO())
    @Override
    public AccountInfo toDeepCopyDomainInfo() {
        // Note: *MUST* return a deep copy

        return new AccountInfo(
                this.id(),
                this.name,
                this.accountStatus,
                this.currentFinePennies,
                this.maxAcceptedBooks,
                this.maxFinePennies,
                new HashMap<>(this.timeStampToAccountAuditLogItemMap)  // deep copy
        );
    }
}
