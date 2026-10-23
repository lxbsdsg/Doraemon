package com.cofbro.qian.utils

import com.alibaba.fastjson.JSONObject
import com.cofbro.qian.mapsetting.util.PreWeb
import org.jsoup.Jsoup
import java.net.URLEncoder

object HtmlParser {
    fun parseImToken(html: String): JSONObject {
        val data = JSONObject()
        val doc = Jsoup.parse(html)
        data["imToken"] = doc.getElementById("myToken") ?: ""
        data["tid"] = doc.getElementById("myTuid") ?: ""
        return data
    }


    fun parseHomeworkHTML(html: String): List<JSONObject> {
        val jsonObject = arrayListOf<JSONObject>()
        try {
            val doc = Jsoup.parse(html)
            val li = doc.getElementsByClass("bottomList").getOrNull(0)?.select("ul > li")
            li?.forEach { item ->
                if (!item.hasAttr("data")) return@forEach
                val data = JSONObject()
                val jumpUrl = item.attr("data")
                data["url"] = jumpUrl
                data["title"] = item.getElementsByClass("overHidden2").getOrNull(0)?.text() ?: ""
                data["status"] = item.getElementsByClass("status").getOrNull(0)?.text() ?: ""
                data["deadline"] = item.getElementsByClass("time").getOrNull(0)?.ownText() ?: ""
                jsonObject.add(data)
            }
        } catch (_: Exception) {
        }
        return jsonObject
    }

    fun parseHomeworkPageCount(html: String): Int {
        return Regex("pageNum\\s*:\\s*(\\d+)").find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 1
    }

    fun parsePostHomeworkUrl(html: String): JSONObject {
        val data = JSONObject()
        val doc = Jsoup.parse(html)
        val form = doc.getElementById("submitForm")
        val url = form?.attr("action") ?: ""
        val workType = doc.getElementsByClass("type_tit")?.getOrNull(0)?.text() ?: ""
        val description = doc.getElementsByClass("mark_name colorDeep fontLabel")?.getOrNull(0)?.text() ?: ""
        val questionIds = doc.getElementsByClass("questionLi")
            ?.mapNotNull { it.attr("data").ifEmpty { null } }
            ?.distinct()
            ?: emptyList()
        val builder = StringBuilder()
        form?.select("input[name]")?.forEach { input ->
            val name = input.attr("name")
            if (name == "answerwqbid") return@forEach
            if (builder.isNotEmpty()) builder.append("&")
            builder.append(name)
                .append("=")
                .append(URLEncoder.encode(input.attr("value"), "UTF-8"))
        }
        if (builder.isNotEmpty()) builder.append("&")
        builder.append("answerwqbid=")
            .append(URLEncoder.encode(questionIds.joinToString(",") + ",", "UTF-8"))
        questionIds.forEach { questionId ->
            builder.append("&answertype").append(questionId).append("=4")
            builder.append("&answer").append(questionId).append("=")
        }
        data[Constants.Work.WORK_TYPE] = workType
        data[Constants.Work.DESCRIPTION] = description
        data[Constants.Work.SUBMIT_URL] = "https://mooc1.chaoxing.com$url&ua=pc&formType=post&saveStatus=1&version=1"
        data[Constants.Work.PREFIX_URL] = builder.toString()
        return data
    }


    fun findEnc(html: String): String {
        val doc = Jsoup.parse(html)
        return doc.getElementById("workEnc")?.`val`() ?: ""
    }

    fun parseToUsername(html: String): String {
        val doc = Jsoup.parse(html)
        return doc.getElementsByClass("user-con")?.text() ?: "未查询到姓名"
    }

    fun parseToSignCode(html: String): String {
        val doc = Jsoup.parse(html)
        return doc.getElementById("signCode")?.`val`() ?: ""
    }
    fun parsePreSignWebGet(it:String): PreWeb {
        val html = Jsoup.parse(it)
        val locationText = html.getElementById("locationText")?.`val`()
        val latitude = html.getElementById("locationLatitude")?.`val`()
        val longitude = html.getElementById("locationLongitude")?.`val`()
        val statusContent =
            html.getElementsByClass("zsign_success zsign_hook").select(">h1").text()
        return PreWeb(html, locationText, latitude, longitude, statusContent)
    }

}
