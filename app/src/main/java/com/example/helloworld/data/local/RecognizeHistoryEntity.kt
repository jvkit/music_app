package com.example.helloworld.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 识曲历史。
 *
 * `id` 用识别发生的毫秒时间戳（每次识别只会存一条），`tracksJson` 是一次识别的**全部**结果
 * 序列化后的数组——识曲常常一次匹配到多首，历史里要能展开看全，而不是只记第一首。
 */
@Entity(tableName = "recognize_history")
data class RecognizeHistoryEntity(
    @PrimaryKey val id: Long,
    /** 识别发生时间 */
    val ts: Long,
    /** 结果数量（列表页不用解析 JSON 就能显示"共 N 首"） */
    val trackCount: Int,
    /** [com.example.helloworld.data.remote.ApiTrack] 数组的 JSON */
    val tracksJson: String,
    /** 首首都为空时的失败原因，供历史里展示 */
    val note: String = ""
)

@Dao
interface RecognizeHistoryDao {
    @Query("SELECT * FROM recognize_history ORDER BY ts DESC LIMIT 50")
    fun observe(): Flow<List<RecognizeHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RecognizeHistoryEntity)

    @Query("DELETE FROM recognize_history")
    suspend fun clearAll()

    @Query("DELETE FROM recognize_history WHERE id = :id")
    suspend fun delete(id: Long)
}
