package domain.account.data;

import org.elegantobjects.jpages.LibraryApp.common.util.Result;
import org.elegantobjects.jpages.LibraryApp.common.util.uuid2.UUID2;
import org.elegantobjects.jpages.LibraryApp.domain.account.Account;
import org.elegantobjects.jpages.LibraryApp.domain.common.data.repo.IRepo;

/**
 * IAccountInfoRepo is an interface for the AccountInfoRepo class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

public interface IAccountInfoRepo extends IRepo {
    Result<AccountInfo> fetchAccountInfo(UUID2<Account> id);
    Result<AccountInfo> updateAccountInfo(AccountInfo accountInfo);
    Result<AccountInfo> upsertAccountInfo(AccountInfo accountInfo);
}
