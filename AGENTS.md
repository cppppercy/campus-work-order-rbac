# Project working agreement

All project implementation belongs in this directory. Java 17, Swing, SQLite JDBC, Maven and JUnit 5 are the agreed stack. Keep UI, events, services, repositories and infrastructure separate. Every protected service operation checks current database permissions using a trusted session. Never grant privileges by username or role name.

Use UTF-8. Do not commit databases, credentials, build outputs or portable tools. Record only actually executed test results. Run `mvnw.cmd clean verify` before final delivery.

Parallel ownership: database agent owns `infrastructure/Database.java`, `infrastructure/Passwords.java`, SQL resources and DatabaseTest; security agent owns `service/SecurityServices.java`, `infrastructure/Access.java`, `infrastructure/SessionRegistry.java` and SecurityTest; GUI agent owns `ui/`, `event/` and GUI tests. Root owns shared contracts, work-order services, build, integration, documentation and Git operations. Do not modify another owner's files without agreement.
