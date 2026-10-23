package com.pocket.xiaoli.data.repository

import com.pocket.xiaoli.data.dao.CourseDao
import com.pocket.xiaoli.data.dao.TeacherDao
import com.pocket.xiaoli.data.entity.TeacherEntity
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.GoodItemEntity
import com.pocket.xiaoli.data.entity.TreeHoleEntity
import com.pocket.xiaoli.data.entity.HomeworkEntity
import com.pocket.xiaoli.data.entity.SchemeEntity
import com.pocket.xiaoli.data.entity.TaskEntity
import com.pocket.xiaoli.data.entity.TermEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import kotlinx.coroutines.flow.Flow
import com.pocket.xiaoli.data.dao.ExamDao
import com.pocket.xiaoli.data.entity.ExamEntity

class CourseRepository(
    private val dao: CourseDao,
    private val teacherDao: TeacherDao,
    private val examDao: ExamDao,
) {
    val allCourses: Flow<List<CourseEntity>> = dao.getAllCourses()
    val allTimeSlots: Flow<List<TimeSlotEntity>> = dao.getAllTimeSlots()
    val allSchemes: Flow<List<SchemeEntity>> = dao.getAllSchemes()
    val allTerms: Flow<List<TermEntity>> = dao.getAllTerms()

    fun getCoursesByScheme(schemeId: Long): Flow<List<CourseEntity>> = dao.getCoursesByScheme(schemeId)

    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun insert(course: CourseEntity) = dao.insert(course)
    suspend fun update(course: CourseEntity) = dao.update(course)
    suspend fun moveByDelta(ids: List<Long>, dayDelta: Int, slotDelta: Int): Int =
        if (ids.isEmpty() || (dayDelta == 0 && slotDelta == 0)) 0
        else dao.moveByDelta(ids, dayDelta, slotDelta)
    suspend fun delete(course: CourseEntity) = dao.delete(course)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun deleteByScheme(schemeId: Long) = dao.deleteByScheme(schemeId)
    suspend fun deleteByTimeSlot(day: Int, startSlot: Int, endSlot: Int, schemeId: Long) =
        dao.deleteByTimeSlot(day, startSlot, endSlot, schemeId)
    suspend fun deleteByName(name: String, schemeId: Long) = dao.deleteByName(name, schemeId)
    suspend fun insertAll(courses: List<CourseEntity>) {
        if (courses.isNotEmpty()) dao.insertAll(courses)
    }
    suspend fun updateAll(courses: List<CourseEntity>) {
        if (courses.isNotEmpty()) dao.updateAll(courses)
    }

    // ── Scheme operations ──
    suspend fun createScheme(name: String, termId: Long = 0L): Long {
        val count = dao.getSchemeCount()
        return dao.insertScheme(SchemeEntity(name = name, sortOrder = count, termId = termId))
    }
    suspend fun updateScheme(scheme: SchemeEntity) = dao.updateScheme(scheme)
    suspend fun deleteSchemeCascade(id: Long) = dao.deleteSchemeCascade(id)
    suspend fun getSchemeById(id: Long) = dao.getSchemeById(id)
    fun getSchemesByTerm(termId: Long) = dao.getSchemesByTerm(termId)
    suspend fun getSchemesByTermOnce(termId: Long) = dao.getSchemesByTermOnce(termId)

    // ── Term operations（学期是一级实体，方案挂在学期下） ──
    suspend fun getAllTermsOnce() = dao.getAllTermsOnce()
    suspend fun getTermById(id: Long) = dao.getTermById(id)
    suspend fun createTerm(name: String, startDate: String = "", totalWeeks: Int = 20): Long {
        val count = dao.getTermCount()
        return dao.insertTerm(TermEntity(name = name, startDate = startDate, totalWeeks = totalWeeks, sortOrder = count))
    }
    suspend fun updateTerm(term: TermEntity) = dao.updateTerm(term)

    /**
     * 把某个学期的开学日期 / 总周数改成给定值。
     *
     * 为什么要这一条：`AppContainer.setSemesterStart/setSemesterWeeks` 是**镜像**（通知 / 小组件 / 日历
     * 都读这两个 Flow），而校历校准、教务导入、校历一键校准这三处入口只写镜像、不知道学期行。
     * 不回写就会出现「学期页显示的开学日期是旧的、切一次学期又跳回去」。找不到行（默认学期尚未落库）就忽略。
     */
    suspend fun patchTermSchedule(termId: Long, startDate: String, totalWeeks: Int) {
        val term = dao.getTermById(termId) ?: return
        dao.updateTerm(term.copy(startDate = startDate, totalWeeks = totalWeeks))
    }
    suspend fun deleteTerm(id: Long) = dao.deleteTerm(id)

    /**
     * 删除学期及其下所有课表方案（方案级联再删课程 / 作业 / 任务 / 好物 / 树洞）。
     * `id = 0` 的默认学期不允许删除，由调用方拦截。
     */
    suspend fun deleteTermCascade(termId: Long) {
        dao.getSchemesByTermOnce(termId).forEach { dao.deleteSchemeCascade(it.id) }
        dao.deleteTerm(termId)
    }

    suspend fun deleteAllTimeSlots() = dao.deleteAllTimeSlots()
    suspend fun replaceTimeSlots(slots: List<TimeSlotEntity>) = dao.replaceAllTimeSlots(slots)

    // ── Homework operations ──
    fun getHomeworkByScheme(schemeId: Long) = dao.getHomeworkByScheme(schemeId)
    fun getAllHomework() = dao.getAllHomework()
    suspend fun insertHomework(homework: HomeworkEntity) = dao.insertHomework(homework)
    suspend fun updateHomework(homework: HomeworkEntity) = dao.updateHomework(homework)
    suspend fun setHomeworkCompleted(id: Long, completed: Boolean) = dao.setHomeworkCompleted(id, completed)
    suspend fun deleteHomework(id: Long) = dao.deleteHomework(id)
    fun getActiveHomeworkCourseNames(schemeId: Long) = dao.getActiveHomeworkCourseNames(schemeId)

    // ── Exam operations（考试安排：来自通知解析 + 用户确认） ──
    fun getExamsByScheme(schemeId: Long) = examDao.getByScheme(schemeId)
    fun getAllExams() = examDao.getAll()
    suspend fun insertExam(exam: ExamEntity) = examDao.insert(exam)
    suspend fun insertExams(exams: List<ExamEntity>) = examDao.insertAll(exams)
    suspend fun setExamCompleted(id: Long, completed: Boolean) = examDao.setCompleted(id, completed)
    suspend fun deleteExam(id: Long) = examDao.deleteById(id)
    suspend fun deleteExamsByScheme(schemeId: Long) = examDao.deleteByScheme(schemeId)

    /** 已存在的「课程+日期+时间」键，用于导入去重。 */
    suspend fun existingExamKeys() = examDao.existingKeys()

    // ── Task operations ──
    fun getTasksByScheme(schemeId: Long) = dao.getTasksByScheme(schemeId)
    suspend fun insertTask(task: TaskEntity) = dao.insertTask(task)
    suspend fun updateTask(task: TaskEntity) = dao.updateTask(task)
    suspend fun deleteTask(id: Long) = dao.deleteTask(id)

    // ── Good Item operations ──
    fun getGoodItemsByScheme(schemeId: Long) = dao.getGoodItemsByScheme(schemeId)
    suspend fun insertGoodItem(item: GoodItemEntity) = dao.insertGoodItem(item)
    suspend fun updateGoodItem(item: GoodItemEntity) = dao.updateGoodItem(item)
    suspend fun deleteGoodItem(id: Long) = dao.deleteGoodItem(id)

    // ── Tree Hole operations ──
    fun getTreeHolesByScheme(schemeId: Long) = dao.getTreeHolesByScheme(schemeId)
    suspend fun insertTreeHole(item: TreeHoleEntity) = dao.insertTreeHole(item)
    suspend fun deleteTreeHole(id: Long) = dao.deleteTreeHole(id)

    // ── Teacher operations ──
    fun getAllTeachers(): Flow<List<TeacherEntity>> = teacherDao.getAllTeachers()
    suspend fun getTeacherByName(name: String): TeacherEntity? = teacherDao.getTeacherByName(name)
    suspend fun insertTeacher(teacher: TeacherEntity) = teacherDao.insertTeacher(teacher)
    suspend fun deleteTeacher(name: String) = teacherDao.deleteTeacher(name)
    fun getDistinctTeacherNames(schemeId: Long): Flow<List<String>> = teacherDao.getDistinctTeacherNames(schemeId)

    // 首次启动填充默认12节课时间段；课表页在时间段为空时调用
    suspend fun initTimeSlots() {
        val defaultSlots = listOf(
            TimeSlotEntity(1, "08:00", "08:45"),
            TimeSlotEntity(2, "08:55", "09:40"),
            TimeSlotEntity(3, "10:00", "10:45"),
            TimeSlotEntity(4, "10:55", "11:40"),
            TimeSlotEntity(5, "13:30", "14:15"),
            TimeSlotEntity(6, "14:25", "15:10"),
            TimeSlotEntity(7, "15:30", "16:15"),
            TimeSlotEntity(8, "16:25", "17:10"),
            TimeSlotEntity(9, "18:30", "19:15"),
            TimeSlotEntity(10, "19:25", "20:10"),
            TimeSlotEntity(11, "20:20", "21:05"),
            TimeSlotEntity(12, "21:15", "22:00")
        )
        dao.insertTimeSlots(defaultSlots)
    }
}
