package com.pocket.xiaoli.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pocket.xiaoli.data.entity.AssistantConversationEntity
import com.pocket.xiaoli.data.entity.AssistantMessageEntity
import com.pocket.xiaoli.data.entity.AcademicSnapshotEntity
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.GoodItemEntity
import com.pocket.xiaoli.data.entity.HomeworkEntity
import com.pocket.xiaoli.data.entity.TreeHoleEntity
import com.pocket.xiaoli.data.entity.SavedGradeEntity
import com.pocket.xiaoli.data.entity.SpecialWorkDayEntity
import com.pocket.xiaoli.data.entity.CampusMapCorrectionEntity
import com.pocket.xiaoli.data.entity.SchemeEntity
import com.pocket.xiaoli.data.entity.TaskEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import com.pocket.xiaoli.data.entity.TeacherEntity
import com.pocket.xiaoli.data.entity.TermEntity
import com.pocket.xiaoli.data.dao.AssistantChatDao
import com.pocket.xiaoli.data.dao.AcademicSnapshotDao
import com.pocket.xiaoli.data.dao.CourseDao
import com.pocket.xiaoli.data.dao.SavedGradeDao
import com.pocket.xiaoli.data.dao.SpecialWorkDayDao
import com.pocket.xiaoli.data.dao.CampusMapCorrectionDao
import com.pocket.xiaoli.data.dao.TeacherDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.pocket.xiaoli.data.entity.ExamEntity
import com.pocket.xiaoli.data.dao.ExamDao
import com.pocket.xiaoli.data.entity.FocusSessionEntity
import com.pocket.xiaoli.data.dao.FocusSessionDao

@Database(entities = [CourseEntity::class, TimeSlotEntity::class, SchemeEntity::class, HomeworkEntity::class, TaskEntity::class, GoodItemEntity::class, TreeHoleEntity::class, TeacherEntity::class, SavedGradeEntity::class, SpecialWorkDayEntity::class, CampusMapCorrectionEntity::class, AssistantConversationEntity::class, AssistantMessageEntity::class, ExamEntity::class, AcademicSnapshotEntity::class, FocusSessionEntity::class, TermEntity::class], version = 20, exportSchema = true)
abstract class PocketXiaoliDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun teacherDao(): TeacherDao
    abstract fun savedGradeDao(): SavedGradeDao
    abstract fun specialWorkDayDao(): SpecialWorkDayDao
    abstract fun campusMapCorrectionDao(): CampusMapCorrectionDao
    abstract fun assistantChatDao(): AssistantChatDao
    abstract fun examDao(): ExamDao
    abstract fun academicSnapshotDao(): AcademicSnapshotDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        @Volatile private var INSTANCE: PocketXiaoliDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v1→v2: no schema change; ensures migration path is defined
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS schemes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("ALTER TABLE courses ADD COLUMN schemeId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("INSERT INTO schemes (id, name, sortOrder) VALUES (0, '默认课表', 0)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS homework (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, courseName TEXT NOT NULL, content TEXT NOT NULL DEFAULT '', dueDate TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 0, schemeId INTEGER NOT NULL DEFAULT 0)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, completed INTEGER NOT NULL DEFAULT 0, sortOrder INTEGER NOT NULL DEFAULT 0, schemeId INTEGER NOT NULL DEFAULT 0)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS good_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL DEFAULT '', reason TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '', referencePrice TEXT NOT NULL DEFAULT '', purchased INTEGER NOT NULL DEFAULT 0, sortOrder INTEGER NOT NULL DEFAULT 0, schemeId INTEGER NOT NULL DEFAULT 0)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS tree_holes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL DEFAULT 0, schemeId INTEGER NOT NULL DEFAULT 0)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tree_holes ADD COLUMN author TEXT NOT NULL DEFAULT ''")
                // Seed default tree hole messages
                db.execSQL("INSERT INTO tree_holes (content, author, createdAt, schemeId) VALUES ('口袋小鲤好吃', '爱吃口袋小鲤', ${System.currentTimeMillis()}, 0)")
                db.execSQL("INSERT INTO tree_holes (content, author, createdAt, schemeId) VALUES ('加油', '梦梦', ${System.currentTimeMillis()}, 0)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS teachers (name TEXT PRIMARY KEY NOT NULL, office TEXT NOT NULL DEFAULT '', officeHours TEXT NOT NULL DEFAULT '', phone TEXT NOT NULL DEFAULT '', title TEXT NOT NULL DEFAULT '', lastUpdated INTEGER NOT NULL DEFAULT 0)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reserved: edu_accounts table was planned but never used; keep migration path
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // saved_grades：已修课程成绩存档（列与 SavedGradeEntity 精确一致，Room 校验要求）
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS saved_grades (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "courseName TEXT NOT NULL, " +
                        "totalScore TEXT NOT NULL, " +
                        "credits TEXT NOT NULL, " +
                        "gpa TEXT NOT NULL, " +
                        "regular TEXT NOT NULL, " +
                        "final TEXT NOT NULL, " +
                        "midterm TEXT NOT NULL, " +
                        "examType TEXT NOT NULL, " +
                        "termLabel TEXT NOT NULL, " +
                        "schoolName TEXT NOT NULL, " +
                        "savedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_saved_grades_courseName_termLabel_totalScore " +
                        "ON saved_grades(courseName, termLabel, totalScore)"
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 调休上班日映射：origin_date 为调休日，target_date 为按哪天的课表上课
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS special_work_day (" +
                        "origin_date TEXT NOT NULL, " +
                        "target_date TEXT NOT NULL, " +
                        "PRIMARY KEY(origin_date))"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS campus_map_corrections (" +
                        "id TEXT NOT NULL, " +
                        "campus_id TEXT NOT NULL, " +
                        "kind TEXT NOT NULL, " +
                        "target_id TEXT NOT NULL, " +
                        "name TEXT NOT NULL DEFAULT '', " +
                        "lat REAL, " +
                        "lng REAL, " +
                        "building_id TEXT, " +
                        "floor_id TEXT, " +
                        "payload TEXT NOT NULL DEFAULT '', " +
                        "updated_at INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(id))"
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 助手会话：一次连续的对话
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS assistant_conversations (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL DEFAULT '', " +
                        "created_at INTEGER NOT NULL DEFAULT 0, " +
                        "updated_at INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_assistant_conversations_updated_at " +
                        "ON assistant_conversations(updated_at)"
                )
                // 助手消息：含 tool 角色，用于恢复多轮工具调用的完整上下文
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS assistant_messages (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "conversation_id INTEGER NOT NULL, " +
                        "role TEXT NOT NULL, " +
                        "content TEXT NOT NULL DEFAULT '', " +
                        "tool_calls TEXT, " +
                        "tool_call_id TEXT, " +
                        "tool_name TEXT, " +
                        "created_at INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_assistant_messages_conversation_id_created_at " +
                        "ON assistant_messages(conversation_id, created_at)"
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 考试安排：教务不提供接口，数据来自「通知文件 → 解析 → 用户确认」
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS exams (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "courseName TEXT NOT NULL, " +
                        "date TEXT NOT NULL DEFAULT '', " +
                        "time TEXT NOT NULL DEFAULT '', " +
                        "location TEXT NOT NULL DEFAULT '', " +
                        "seat TEXT NOT NULL DEFAULT '', " +
                        "completed INTEGER NOT NULL DEFAULT 0, " +
                        "schemeId INTEGER NOT NULL DEFAULT 0)"
                )
                // 首页待办按「未完成 + 日期」查询，这个索引直接服务该路径
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_exams_schemeId_completed_date " +
                        "ON exams(schemeId, completed, date)"
                )
            }
        }

        /**
         * 换掉 exams 上的索引：`(schemeId, completed, date)` → `(schemeId, date, time)`。
         *
         * 起因：14→15 建索引时按"未完成 + 日期"的设想写了 `completed` 在中间，
         * 但 `ExamDao.getByScheme` 实际是 `WHERE schemeId = ? ORDER BY date, time`，
         * 中间列一断，前缀匹配就用不上这条索引；而且**没有任何查询过滤 completed**。
         * 索引顺序现在照抄 WHERE + ORDER BY。
         *
         * 纯 DDL、不动数据；`IF EXISTS` / `IF NOT EXISTS` 让它在"目录里恰好已有该索引"
         * 的边缘情况下也不会抛异常。
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_exams_schemeId_completed_date")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_exams_schemeId_date_time " +
                        "ON exams(schemeId, date, time)"
                )
            }
        }

        /**
         * 课表配色与课程简称（2026-10 的两个 P0）。
         *
         * 加两列：
         * - `shortName`：格子里的简称（详见 `CourseShortName`）。默认空串 = 用全名；
         * - `colorPinned`：颜色是不是用户手选的（详见 `CourseEntity.colorPinned`）。
         *
         * 再**修一次已有数据**（决策在 `CourseRowRepair.plan`，这里只执行）：
         * - 颜色：改造前按 `(星期 * 3 + 节次) % 15` 分配，同一门课在周二/周五不同色 ——
         *   用户会以为是两门课（他报的原话）。看起来是自动值的行重算成按课名派生的稳定色；
         * - 简称：内置表 / 规则能给出行之有效的简称的行，顺手填上（用户已填的不动）。
         *
         * ⚠️ 这是**唯一**会改动用户已有课表数据的一处，判据与"猜错的代价"写在
         * `CourseRowRepair` 的 KDoc 里，务必先读那段再改这里。
         */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN shortName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE courses ADD COLUMN colorPinned INTEGER NOT NULL DEFAULT 0")
                repairCourseRows(db)
            }
        }

        /**
         * 按 [CourseRowRepair.plan] 修 `courses` 的配色与简称。
         *
         * 刻意**不**自带 `beginTransaction`：Room 已经把整个迁移包在一个事务里，
         * 这里再开一层只会让"失败回滚"的语义更难推理。中途抛异常时 Room 会回滚整次迁移，
         * 于是旧库原样留在 v16 —— 可以接受（总好过半个库被改过）。
         */
        private fun repairCourseRows(db: SupportSQLiteDatabase) {
            val rows = mutableListOf<CourseRowRepair.Row>()
            db.query("SELECT id, name, dayOfWeek, startSlot, colorIndex, shortName FROM courses").use { c ->
                while (c.moveToNext()) {
                    rows += CourseRowRepair.Row(
                        id = c.getLong(0),
                        name = c.getString(1).orEmpty(),
                        dayOfWeek = c.getInt(2),
                        startSlot = c.getInt(3),
                        colorIndex = c.getInt(4),
                        shortName = c.getString(5).orEmpty(),
                    )
                }
            }
            for (fix in CourseRowRepair.plan(rows)) {
                db.execSQL(
                    "UPDATE courses SET colorIndex = ?, colorPinned = ?, shortName = ? WHERE id = ?",
                    arrayOf<Any?>(fix.colorIndex, if (fix.colorPinned) 1 else 0, fix.shortName, fix.id),
                )
            }
        }

        /**
         * v17→v18：新增「修课情况」快照表（单行，整份替换）。
         *
         * 纯 `CREATE TABLE`，**没有任何数据搬运** —— 这是刻意的：
         * 本仓库没引 `room-testing`，迁移体在单测里跑不起来，
         * 所以新表只落结构，风险压到最小（改老数据的迁移见 `MIGRATION_16_17` 的说明）。
         * 列定义必须与 `AcademicSnapshotEntity` 的 Room 生成结果逐字一致，
         * 否则 Room 的 schema 校验会在打开库时报错。
         */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS academic_snapshot (" +
                        "id INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL DEFAULT 0, " +
                        "typesJson TEXT NOT NULL DEFAULT '', " +
                        "coursesJson TEXT NOT NULL DEFAULT '', " +
                        "PRIMARY KEY(id))"
                )
            }
        }

        /**
         * v18→v19：新增「番茄钟历史段」表（`focus_sessions`）。
         *
         * 纯 `CREATE TABLE` + `CREATE INDEX`，**没有任何数据搬运** —— 同上一条的理由：
         * 本仓库没引 `room-testing`，迁移体在单测里跑不起来，所以新表只落结构。
         * 列定义必须与 `FocusSessionEntity` 的 Room 生成结果逐字一致（含
         * `completed` 落成 `INTEGER NOT NULL`、`linkedTaskId` 可空落成裸 `INTEGER`），
         * 否则 Room 的 schema 校验会在打开库时报错。
         *
         * 索引 `endedAtWallMs` 对应 `FocusSessionDao` 的两个查询：
         * `WHERE endedAtWallMs >= ?` 与 `ORDER BY endedAtWallMs DESC LIMIT 1`。
         */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS focus_sessions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "phase TEXT NOT NULL, " +
                        "roundIndex INTEGER NOT NULL, " +
                        "plannedMs INTEGER NOT NULL, " +
                        "actualMs INTEGER NOT NULL, " +
                        "startedAtWallMs INTEGER NOT NULL, " +
                        "endedAtWallMs INTEGER NOT NULL, " +
                        "completed INTEGER NOT NULL, " +
                        "linkedTaskId INTEGER)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_focus_sessions_endedAtWallMs " +
                        "ON focus_sessions(endedAtWallMs)"
                )
            }
        }

        /**
         * v19→v20：学期从「一对全局 SharedPreferences」升成一级实体（照岱宗盒子的 termCode），
         * 课表方案挂到学期下；课程补上日程备注与分钟级时间。
         *
         * 旧数据全部归入 `terms.id = 0` 的「默认学期」（`schemes.termId DEFAULT 0` 已覆盖）。
         * `weekType` 的 odd/even 在这里展开成具体的 `weeks` 列表，随后统一置为 `all` ——
         * 岱宗用 `weekBits` 位图做单一真源；本项目不引入位图（那会变成第三份周次表示），
         * 而是让 `weeks` 成为唯一的周次来源，`weekType` 只作为迁移遗留列保留。
         */
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS terms (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "startDate TEXT NOT NULL, " +
                        "totalWeeks INTEGER NOT NULL, " +
                        "sortOrder INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO terms (id, name, startDate, totalWeeks, sortOrder) " +
                        "VALUES (0, '默认学期', '', 20, 0)"
                )
                db.execSQL("ALTER TABLE schemes ADD COLUMN termId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE courses ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE courses ADD COLUMN startMinute INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE courses ADD COLUMN endMinute INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE courses ADD COLUMN kind TEXT NOT NULL DEFAULT 'course'")

                // 奇/偶周 → 具体周次列表：迁移后 weeks 成为唯一真源，weekType 退化为遗留列
                db.query(
                    "SELECT id, weekType FROM courses " +
                        "WHERE weekType IN ('odd', 'even') AND (weeks IS NULL OR weeks = '')"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val isOdd = cursor.getString(1) == "odd"
                        val weeks = (1..30).filter { (it % 2 == 1) == isOdd }.joinToString(",")
                        db.execSQL(
                            "UPDATE courses SET weeks = ? WHERE id = ?",
                            arrayOf<Any>(weeks, id)
                        )
                    }
                }
                db.execSQL("UPDATE courses SET weekType = 'all' WHERE weekType <> 'all'")
            }
        }

        /** Ensure default scheme exists on fresh installs (DB created at v4, no migration runs). */
        private val CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL("INSERT OR IGNORE INTO schemes (id, name, sortOrder, termId) VALUES (0, '默认课表', 0, 0)")
                db.execSQL("INSERT OR IGNORE INTO terms (id, name, startDate, totalWeeks, sortOrder) VALUES (0, '默认学期', '', 20, 0)")
                db.execSQL("INSERT OR IGNORE INTO time_slots (slot, startTime, endTime) VALUES (1, '08:00', '08:45')")
                db.execSQL("INSERT OR IGNORE INTO time_slots (slot, startTime, endTime) VALUES (2, '08:55', '09:40')")
                db.execSQL("INSERT OR IGNORE INTO time_slots (slot, startTime, endTime) VALUES (3, '10:00', '10:45')")
                db.execSQL("INSERT OR IGNORE INTO time_slots (slot, startTime, endTime) VALUES (4, '10:55', '11:40')")
                db.execSQL("INSERT OR IGNORE INTO time_slots (slot, startTime, endTime) VALUES (5, '13:30', '14:15')")
                db.execSQL("INSERT OR IGNORE INTO time_slots (slot, startTime, endTime) VALUES (6, '14:25', '15:10')")
                db.execSQL("INSERT OR IGNORE INTO time_slots (slot, startTime, endTime) VALUES (7, '15:30', '16:15')")
                db.execSQL("INSERT OR IGNORE INTO time_slots (slot, startTime, endTime) VALUES (8, '16:25', '17:10')")
                db.execSQL("INSERT OR IGNORE INTO time_slots (slot, startTime, endTime) VALUES (9, '18:30', '19:15')")
                db.execSQL("INSERT OR IGNORE INTO time_slots (slot, startTime, endTime) VALUES (10, '19:25', '20:10')")
                db.execSQL("INSERT OR IGNORE INTO time_slots (slot, startTime, endTime) VALUES (11, '20:20', '21:05')")
                db.execSQL("INSERT OR IGNORE INTO time_slots (slot, startTime, endTime) VALUES (12, '21:15', '22:00')")
            }
        }

        fun getInstance(context: Context): PocketXiaoliDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context, PocketXiaoliDatabase::class.java, "pocket_xiaoli.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                    .addCallback(CALLBACK)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
