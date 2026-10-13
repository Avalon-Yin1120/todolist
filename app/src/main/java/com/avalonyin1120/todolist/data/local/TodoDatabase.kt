package com.avalonyin1120.todolist.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [TodoEntity::class],
    // 版本历史：v1 初始；v2 周期与提醒方式；v3 周几/几号；v4 重复截止与次数；v5 生效起始日期。
    version = 5,
    exportSchema = true,
)
abstract class TodoDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao

    companion object {
        /** 数据库文件名；测试用它做隔离，因此声明为 internal 供同模块复用。 */
        internal const val DB_NAME = "todolist.db"

        /**
         * 数据库版本，供「设置 → 版本号」展示与「必须配套迁移」的自检使用。
         *
         * 与上面 `@Database(version = ...)` 必须保持一致（注解参数不能引用本类的
         * companion 常量，只能各写一份，改的时候两处一起改）。
         */
        const val DB_VERSION = 5

        /**
         * 版本升级用的迁移。
         *
         * **这里是空的，但不再是「随便清库」**：测试阶段的 `fallbackToDestructiveMigration`
         * 已经移除。理由：本版本把包名从 `com.example.todolist` 改成了
         * `com.avalonyin1120.todolist`，对所有安装而言都是一次**全新安装**，
         * 历史库不会带过来，所以现在没有需要迁移的旧数据；
         * 而从 1.0.0 起用户数据就是真实资产了，**任何 schema 变更都必须配一条 Migration**，
         * 忘写就会在升级时直接抛 `IllegalStateException`（开发期立刻暴露，而不是悄悄清空用户数据）。
         *
         * 加迁移的做法（以 v5 → v6 为例）：
         * 1. 改 `@Database(version = 6)` 并把 [DB_VERSION] 一起改成 6；
         * 2. 用 Room 导出的 `app/schemas/.../5.json` 与 `6.json` 对比差异；
         * 3. 写 `val MIGRATION_5_6 = object : Migration(5, 6) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE todos ADD COLUMN xxx INTEGER NOT NULL DEFAULT 0") } }`；
         * 4. 加进下面的数组，并补一条 Room 的 `MigrationTestHelper` 测试。
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        @Volatile
        private var instance: TodoDatabase? = null

        fun get(context: Context): TodoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TodoDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(*MIGRATIONS)
                .build()
                .also { instance = it }
        }
    }
}
