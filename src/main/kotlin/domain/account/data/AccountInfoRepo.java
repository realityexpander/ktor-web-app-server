package domain.account.data;

import org.elegantobjects.jpages.LibraryApp.common.util.Result;
import org.elegantobjects.jpages.LibraryApp.common.util.log.ILog;
import org.elegantobjects.jpages.LibraryApp.common.util.uuid2.UUID2;
import org.elegantobjects.jpages.LibraryApp.domain.account.Account;
import org.elegantobjects.jpages.LibraryApp.domain.common.data.repo.Repo;
import org.jetbrains.annotations.NotNull;

/**
 * AccountInfoRepo is a Repo for AccountInfo objects.<br>
 * <br>
 * Holds Account info for all the users in the system (simple CRUD operations)<br>
 * <br>
 * Simulates a database on a server via in-memory HashMap.<br>
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

public class AccountInfoRepo extends Repo implements IAccountInfoRepo {

    // simulate a local database on server (UUID2<Account> is the key)
    private final UUID2.HashMap<UUID2<Account>, AccountInfo> database = new UUID2.HashMap<>();

    public
    AccountInfoRepo(@NotNull ILog log) {
        super(log);
    }

    public Result<AccountInfo> fetchAccountInfo(UUID2<Account> id) {
        log.d(this, "id: " + id);

        // Simulate network/database
        if (database.containsKey(id)) {
            return new Result.Success<>(database.get(id));
        }

        return new Result.Failure<>(new Exception("Repo.AccountInfo, account not found, id: " + id));
    }

    @Override
    public Result<AccountInfo> updateAccountInfo(@NotNull AccountInfo accountInfo) {
        log.d(this, "accountInfo.id: " + accountInfo.id());

        // Simulate network/database
        if (database.containsKey(accountInfo.id())) {
            database.put(accountInfo.id(), accountInfo);

            return new Result.Success<>(accountInfo);
        }

        return new Result.Failure<>(new Exception("Repo.AccountInfo, account not found, id: " + accountInfo.id()));
    }

    @Override
    public Result<AccountInfo> upsertAccountInfo(@NotNull AccountInfo accountInfo) {
        log.d(this, "accountInfo.id(): " + accountInfo.id());

        // Simulate network/database
        database.put(accountInfo.id(), accountInfo);

        return new Result.Success<>(accountInfo);
    }
}