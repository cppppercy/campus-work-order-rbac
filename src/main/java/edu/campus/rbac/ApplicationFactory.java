package edu.campus.rbac;

import edu.campus.rbac.infrastructure.Database;
import edu.campus.rbac.service.*;
import java.nio.file.Path;

/** 应用组合入口；测试与 GUI 使用同一套真实服务。 */
public final class ApplicationFactory {
    private ApplicationFactory() {}
    public static Services.Bundle open(Path path) {
        Database database=new Database(path);
        database.initialize();
        SecurityServices security=new SecurityServices(database);
        BusinessServices business=new BusinessServices(security.access());
        return new Services.Bundle(security.authentication(),security.permissions(),security.users(),security.roles(),business,business,business);
    }
}
