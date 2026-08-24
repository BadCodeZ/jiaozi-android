package com.jiaozi.sz.data

import android.content.Context
import com.jiaozi.sz.data.model.AutoSyllSubj
import com.jiaozi.sz.data.model.Bank
import com.jiaozi.sz.data.model.Knowledge
import com.jiaozi.sz.data.model.SyllabusSubject
import kotlinx.serialization.json.Json

/** 从 assets/ 加载内置数据（首次启动调用一次） */
object AssetLoader {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun loadBank(context: Context): Bank = open(context, "bank.json")
    fun loadSyllabus(context: Context): List<SyllabusSubject> = open(context, "default_syllabus.json")
    fun loadAutoSyll(context: Context): List<AutoSyllSubj> = open(context, "auto_syll.json")
    fun loadKnowledge(context: Context): List<Knowledge> = open(context, "knowledge.json")

    private inline fun <reified T> open(context: Context, name: String): T {
        val text = context.assets.open(name).bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }
}
