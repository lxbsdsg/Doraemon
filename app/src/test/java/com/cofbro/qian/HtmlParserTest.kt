package com.cofbro.qian

import com.cofbro.qian.utils.HtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlParserTest {

    private val listHtml = """
        <html><body>
        <div class="bottomList"><ul>
        <li onclick="goTask(this);" data="https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=1">
            <div class="right-content">
                <p class="overHidden2 fl">作业一标题</p>
                <p class="status fl">未交</p>
            </div>
            <div class="time notOver"><img src="x.png"/>剩余28小时18分钟</div>
        </li>
        <li data="https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=2">
            <p class="overHidden2 fl">作业二标题</p>
            <p class="status fl">已互评</p>
        </li>
        </ul></div>
        <script>$("#page").paging({nowPage:1,pageNum:2});</script>
        </body></html>
    """.trimIndent()

    private val taskHtml = """
        <html><body>
        <form action="/mooc-ans/work/addStudentWorkNewWeb?_classId=151&courseid=265&token=abc" method="post" id="submitForm">
            <input type="hidden" id="courseId" name="courseId" value="265"/>
            <input type="hidden" id="classId" name="classId" value="151"/>
            <input type="hidden" id="cpi" name="cpi" value="483"/>
            <input type="hidden" id="workId" name="workRelationId" value="573"/>
            <input type="hidden" id="answerId" name="workAnswerId" value="556"/>
            <input type="hidden" id="standardEnc" name="standardEnc" value="enc1"/>
            <input type="hidden" id="enc_work" name="enc_work" value="enc2"/>
            <input type="hidden" id="totalQuestionNum" name="totalQuestionNum" value="hash"/>
            <input type="hidden" id="questionIds" name="answerwqbid" value=""/>
            <input type="hidden" id="mooc2" name="mooc2" value="1"/>
            <input type="hidden" id="randomOptions" name="randomOptions" value="false"/>
        </form>
        <div class="padBom50 questionLi fontLabel singleQuesId" id="question123" data="123"></div>
        <h2 class="type_tit">一. 简答题（共1题，10分）</h2>
        <div class="mark_name colorDeep fontLabel">1. 请简述</div>
        </body></html>
    """.trimIndent()

    @Test
    fun parseHomeworkHTML_extractsFieldsByClass() {
        val items = HtmlParser.parseHomeworkHTML(listHtml)
        assertEquals(2, items.size)
        assertEquals("作业一标题", items[0]["title"])
        assertEquals("未交", items[0]["status"])
        assertEquals("剩余28小时18分钟", items[0]["deadline"])
        assertTrue(items[0]["url"].toString().contains("work/task"))
        assertEquals("已互评", items[1]["status"])
    }

    @Test
    fun parseHomeworkPageCount_readsPagingScript() {
        assertEquals(2, HtmlParser.parseHomeworkPageCount(listHtml))
        assertEquals(1, HtmlParser.parseHomeworkPageCount("<html></html>"))
    }

    @Test
    fun parsePostHomeworkUrl_buildsSerializedSubmitBody() {
        val data = HtmlParser.parsePostHomeworkUrl(taskHtml)
        assertTrue(data["url"].toString().startsWith("https://mooc1.chaoxing.com/mooc-ans/work/addStudentWorkNewWeb"))
        assertTrue(data["url"].toString().contains("ua=pc&formType=post&saveStatus=1&version=1"))
        val prefix = data["prefixPostUrl"].toString()
        assertTrue(prefix.contains("courseId=265"))
        assertTrue(prefix.contains("standardEnc=enc1"))
        assertTrue(prefix.contains("workRelationId=573"))
        assertTrue(prefix.contains("answerwqbid=123%2C"))
        assertTrue(prefix.contains("answertype123=4"))
        assertTrue(prefix.endsWith("answer123="))
        assertEquals("一. 简答题（共1题，10分）", data["workType"])
    }
}
