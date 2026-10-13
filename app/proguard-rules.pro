# R8 规则（release 构建）。
#
# 原则：能不加就不加。Compose、Room、WorkManager、androidx.core 都自带 consumer 规则，
# 只有「系统按类名实例化、R8 看不到引用」的入口才需要在这里显式保住。
# 每一条都写清为什么，避免以后变成一坨没人敢删的模板。

# 注解本身要留着：Room 的 @Database/@Dao/@Entity 在运行时仍被读取
-keepattributes *Annotation*
# 保留行号，线上崩溃栈才看得懂（配合 mapping.txt 才能还原）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- 由系统按类名实例化的组件 -------------------------------------------------
# Activity / Service / BroadcastReceiver / ContentProvider 在清单里是按类名写的，
# AGP 会自动生成 keep 规则，这里再写一遍是为了「改清单时不至于静默失效」。
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application

# --- Room --------------------------------------------------------------------
# Room 的运行时用反射实例化生成的 `XxxDatabase_Impl`，并按字段名读写 DAO。
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# --- WorkManager -------------------------------------------------------------
# Worker 由 WorkManager 按类名反射创建（我们的 ReminderWorker 是内部类，构造器签名特殊）。
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
# WorkManager 的自定义初始化走 Configuration.Provider
-keep class * implements androidx.work.Configuration$Provider { *; }

# --- 枚举 --------------------------------------------------------------------
# 项目的状态/策略枚举都靠 values()/valueOf()，R8 的枚举优化在跨模块时可能把
# values() 干掉；这里整类保留，代价很小（枚举一共没几个）。
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- 提醒调度 -----------------------------------------------------------------
# AlarmManager 的 PendingIntent 指向的接收器在清单里声明，已由上面的组件规则覆盖；
# 这里只额外保住「被反射调用的调度器实现」不会有，故不写空规则。
