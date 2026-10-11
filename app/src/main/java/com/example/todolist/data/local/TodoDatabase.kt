package com.example.todolist.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TodoEntity::class],
    // v3：周重复精确到星期几、月重复精确到几号
    version = 3,
    exportSchema = true,
)
abstract class TodoDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao

    companion object {
        /** 数据库文件名；测试用它做隔离，因此声明为 internal 供同模块复用。 */
        internal const val DB_NAME = "todolist.db"

        @Volatile
        private var instance: TodoDatabase? = null

        fun get(context: Context): TodoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TodoDatabase::class.java,
                DB_NAME,
            )
                // v1 -> v2 新增周期与提醒方式；v2 -> v3 新增周几/几号。
                // 正式发版前必须替换为真正的 Migration；当前尚未发布，直接重建库。
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
