package domain.account;

import org.elegantobjects.jpages.LibraryApp.common.util.Result;
import org.elegantobjects.jpages.LibraryApp.common.util.uuid2.IUUID2;
import org.elegantobjects.jpages.LibraryApp.common.util.uuid2.UUID2;
import org.elegantobjects.jpages.LibraryApp.domain.Context;
import org.elegantobjects.jpages.LibraryApp.domain.account.data.AccountInfo;
import org.elegantobjects.jpages.LibraryApp.domain.account.data.AccountInfoRepo;
import org.elegantobjects.jpages.LibraryApp.domain.common.Role;
import org.jetbrains.annotations.NotNull;

/**
 * Account Role Object<br>
 * <br>
 * Only interacts with its own Repo, Context, and other Role Objects
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

public class Account extends Role<AccountInfo> implements IUUID2 {
    public final UUID2<Account> id;
    private final AccountInfoRepo repo;

    public
    Account(
        @NotNull AccountInfo info,
        @NotNull Context context
    ) {
        super(info, context);
        this.repo = this.context.accountInfoRepo();
        this.id = info.id();

        context.log.d(this,"Account (" + this.id + ") created from Info");
    }
    public
    Account(
        String json,
        @NotNull Class<AccountInfo> clazz,
        @NotNull Context context
    ) {
        super(json, clazz, context);
        this.repo = this.context.accountInfoRepo();
        this.id = this.info().id();

        context.log.d(this,"Account (" + this.id + ") created from Json with class: " + clazz.getName());
    }
    public
    Account(
        @NotNull UUID2<Account> id,
        @NotNull Context context
    ) {
        super(id, context);
        this.repo = this.context.accountInfoRepo();
        this.id = id;

        context.log.d(this,"Account(" + this.id + ") created using id with no Info");
    }
    public
    Account(@NotNull String json, @NotNull Context context) { this(json, AccountInfo.class, context); }
    public
    Account(@NotNull Context context) {
        this(UUID2.randomUUID2(), context);
    }

    /////////////////////////
    // Static constructors //
    /////////////////////////

    public static Result<Account> fetchAccount(
        @NotNull UUID2<Account> uuid2,
        @NotNull Context context
    ) {
        AccountInfoRepo repo = context.accountInfoRepo();

        Result<AccountInfo> infoResult = repo.fetchAccountInfo(uuid2);
        if (infoResult instanceof Result.Failure) {
            return new Result.Failure<>(((Result.Failure<AccountInfo>) infoResult).exception());
        }

        AccountInfo info = ((Result.Success<AccountInfo>) infoResult).value();
        return new Result.Success<>(new Account(info, context));
    }

    /////////////////////////////////////
    // IRole/UUID2 Required Overrides  //
    /////////////////////////////////////

    @Override
    public Result<AccountInfo> fetchInfoResult() {
        // context.log.d(this, "Account(" + this.id.toString() + ") - fetchInfoResult"); // LEAVE for debugging

        return this.repo.fetchAccountInfo(this.id);
    }

    @Override
    public Result<AccountInfo> updateInfo(@NotNull AccountInfo updatedInfo) {
        // context.log.d(this,"Account (" + this.id.toString() + ") - updateInfo);  // LEAVE for debugging

        // Optimistically Update the cached Info
        super.updateFetchInfoResult(new Result.Success<>(updatedInfo));

        // Update the Repo
        return this.repo.updateAccountInfo(updatedInfo);
    }

    @Override
    public String uuid2TypeStr() {
        return UUID2.calcUUID2TypeStr(this.getClass());
    }

    ///////////////////////////////////////////
    // Account Role Business Logic Methods //
    // - Methods to modify it's LibraryInfo  //
    ///////////////////////////////////////////

    // todo - to be implemented

    public void DumpDB(@NotNull Context context) {
        System.out.println();
        context.log.d(this,"Dumping Account DB:");
        context.log.d(this, this.toJson());
        System.out.println();
    }
}