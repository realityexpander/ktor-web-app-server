package data.local;

import org.elegantobjects.jpages.LibraryApp.common.util.Result;
import org.elegantobjects.jpages.LibraryApp.common.util.uuid2.IUUID2;
import org.elegantobjects.jpages.LibraryApp.common.util.uuid2.UUID2;
import org.elegantobjects.jpages.LibraryApp.data.network.URL;
import org.elegantobjects.jpages.LibraryApp.domain.common.data.info.local.EntityInfo;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * InMemoryDatabase is an implementation of the IDatabase interface for the EntityInfo class.
 *
 * This class is a stub for a real database implementation.
 *
 * In a real implementation, this class would use a database driver to connect to a database for CRUD operations.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

@SuppressWarnings("FieldCanBeLocal")
public
class InMemoryDatabase<TEntity extends EntityInfo, TUUID2 extends IUUID2> implements IDatabase<TUUID2, TEntity> {
    private final URL url;
    private final String user;
    private final String password;

    // Simulate a local database
    private final UUID2.HashMap<UUID2<TUUID2>, TEntity> database = new UUID2.HashMap<>();

    public InMemoryDatabase(URL url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }
    InMemoryDatabase() {
        this(new URL("memory://hash.map"), "admin", "password");
    }

    @Override
    public Result<TEntity> getEntityInfo(@NotNull UUID2<TUUID2> id) {
        // Simulate the request
        TEntity infoResult =  database.get(id);
        if (infoResult == null) {
            return new Result.Failure<>(new Exception("DB: Failed to get entityInfo, id: " + id));
        }

        return new Result.Success<>(infoResult);
    }

    @Override
    public Result<TEntity> updateEntityInfo(@NotNull TEntity entityInfo) {
        // Simulate the request
        try {
            database.put(entityInfo.id(), entityInfo);
        } catch (Exception e) {
            return new Result.Failure<>(e);
        }

        return new Result.Success<>(entityInfo);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result<TEntity> addEntityInfo(@NotNull TEntity entityInfo) {
        // Simulate the request
        if (database.containsKey((UUID2<TUUID2>) entityInfo.id())) {
            return new Result.Failure<>(new Exception("DB: Entity already exists, did you mean update?, entityInfo: " + entityInfo));
        }
        database.put(entityInfo.id(), entityInfo);

        return new Result.Success<>(entityInfo);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result<TEntity> upsertEntityInfo(@NotNull TEntity entityInfo) {
        if (database.containsKey((UUID2<TUUID2>) entityInfo.id())) {
            return updateEntityInfo(entityInfo);
        } else {
            return addEntityInfo(entityInfo);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result<TEntity> deleteEntityInfo(@NotNull TEntity entityInfo) {
        if (database.remove((UUID2<TUUID2>) entityInfo.id()) == null) {
            return new Result.Failure<>(new Exception("DB: Failed to delete entityInfo, entityInfo: " + entityInfo));
        }

        return new Result.Success<>(entityInfo);
    }

    @Override
    public Map<UUID2<TUUID2>, TEntity> getAllEntityInfo() {

        Map<UUID2<TUUID2>, TEntity> map = new HashMap<>();
        for (Map.Entry<UUID2<TUUID2>, TEntity> entry : database.entrySet()) {
            map.put(new UUID2<>(entry.getKey()), entry.getValue());
        }

        return map;
    }
}