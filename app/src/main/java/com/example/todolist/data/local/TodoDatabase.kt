package com.example.todolist.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TodoEntity::class],
    // v5：重复支持生效起始日期
    version = 5,
    exportSchema = true,
)
abstract class TodoDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao

    companion object {
        /** 数据库文件名；测试用它做隔离，因此声明为 internal 供同模块复用。 */
        internal const val DB_NAME = "todolist.db"

        /**
         * 数据库版本，供「设置 → 版本号」展示。
         *
         * 与上面 `@Database(version = ...)` 必须保持一致（注解参数不能引用本类的
         * companion 常量，只能各写一份，改的时候两处一起改）。
         */
        const val DB_VERSION = 5

        @Volatile
        private var instance: TodoDatabase? = null

        fun get(context: Context): TodoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TodoDatabase::class.java,
                DB_NAME,
            )
                // v1 -> v2 新增周期与提醒方式；v2 -> v3 新增周几/几号；v3 -> v4 新增重复截止与次数；v4 -> v5 新增生效起始日期。
                // 正式发版前必须替换为真正的 Migration；当前尚未发布，直接重建库。
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
