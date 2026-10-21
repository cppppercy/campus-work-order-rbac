package edu.campus.rbac;

import edu.campus.rbac.infrastructure.Database;
import edu.campus.rbac.ui.CampusApplication;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import javax.swing.*;

public final class Main {
    private Main() {}
    public static void main(String[] args) {
        Path db=Path.of("data","campus-rbac.db");boolean initOnly=false;
        try {
            for(int i=0;i<args.length;i++) {
                switch(args[i]) {
                    case "--db" -> {if(++i>=args.length)throw new IllegalArgumentException("--db 需要数据库文件路径");db=Path.of(args[i]);}
                    case "--init-only" -> initOnly=true;
                    case "--help" -> {System.out.println("java -jar campus-work-order-rbac.jar [--db <path>] [--init-only]");return;}
                    default -> throw new IllegalArgumentException("未知参数："+args[i]);
                }
            }
            if(initOnly){new Database(db).initialize();System.out.println("Database initialized: "+db.toAbsolutePath());return;}
            if(GraphicsEnvironment.isHeadless())throw new IllegalStateException("需要图形桌面环境；仅初始化数据库可使用 --init-only");
            // 初始化在主线程执行，之后所有 Swing 组件由 EDT 创建。
            CampusApplication.launch(ApplicationFactory.open(db));
        } catch(Exception e) {
            System.err.println("启动失败："+e.getMessage());
            if(!initOnly&&!GraphicsEnvironment.isHeadless())SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(null,"启动失败："+e.getMessage(),"校园工单系统",JOptionPane.ERROR_MESSAGE));
            else System.exit(1);
        }
    }
}
