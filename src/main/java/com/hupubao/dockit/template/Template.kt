package com.hupubao.dockit.template

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import com.github.jsonzou.jmockdata.JMockData
import com.hupubao.dockit.annotation.Placeholder
import com.hupubao.dockit.entity.Argument
import com.hupubao.dockit.entity.MethodCommentNode
import com.hupubao.dockit.enums.PlaceholderType
import com.hupubao.dockit.resolver.template.PlaceholderResolver
import com.hupubao.dockit.utils.ProjectUtils
import com.vladsch.flexmark.ast.Node
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.sequence.BasedSequence
import org.apache.maven.plugin.logging.Log
import org.apache.maven.project.MavenProject

open class Template(project: MavenProject, log: Log, var source: String, methodCommentNode: MethodCommentNode) {
    lateinit var document: Node

    @Placeholder("title")
    var title: String = ""
    @Placeholder("description", type = PlaceholderType.LIST)
    var descriptionList: MutableList<String> = mutableListOf()
    @Placeholder("requestUrl")
    var requestUrl: String = ""
    @Placeholder("requestMethod")
    var requestMethod: String = ""
    @Placeholder("arg", type = PlaceholderType.LIST)
    var argList: MutableList<Argument> = mutableListOf()
    @Placeholder("resArg", type = PlaceholderType.LIST)
    var resArgList: MutableList<Argument> = mutableListOf()
    @Placeholder("resSample")
    var resSample: String = ""
    @Placeholder("remark")
    var remark: String? = null


    var responseObjectClassName: String? = null
    private var project: MavenProject? = project
    private var log: Log? = log

    init {
        this.title = if (methodCommentNode.title == null) methodCommentNode.methodName!! else methodCommentNode.title!!
        this.descriptionList = methodCommentNode.descriptionList
        this.requestUrl = methodCommentNode.requestUrl
        this.requestMethod = methodCommentNode.requestMethod
        this.argList = methodCommentNode.requestArgList
        this.resArgList = methodCommentNode.responseArgList
        this.responseObjectClassName = methodCommentNode.responseObjectClassName
        this.parse()
    }


    private fun parse() {
        document = Parser.builder().build().parse(source)!!
        resSample = mockResponseData()
        println("mock data:$resSample")
        for (node in document.children) {
            val matchResult = ("""\$\{\w+\.*\w+\}""".toRegex()).findAll(node.chars.toString())
            if (matchResult.none()) {
                continue
            }

            if (matchResult.count() == 1) {
                PlaceholderResolver.resolve(node, mutableListOf(matchResult.single().value), this)
            } else {
                val arr = mutableListOf<String>()
                matchResult.forEach { p ->
                    arr.add(p.value)
                }
                PlaceholderResolver.resolve(node, arr, this)
            }
        }
    }

    private fun mockResponseData(): String {
        var result = ""
        if (responseObjectClassName != null) {

            var subClassName = ""
            if (responseObjectClassName!!.contains("<")) {
                subClassName = responseObjectClassName.substring(responseObjectClassName.indexOf("<"), responseObjectClassName.indexOf(">"))

            }

            val clazzOptional = ProjectUtils.loadClass(project!!, log!!, responseObjectClassName!!)
            clazzOptional.ifPresent { clazz ->
                println("find clazz:$clazz")
                result = mockData(clazz)
            }
        }

        return result
    }

    private fun mockData(clazz: Class<*>): String {
        val data = if (clazz.newInstance() is Iterable<*>) {
            mutableListOf(clazz)
        } else {
            JMockData.mock(clazz)
        }
        return JSON.toJSONString(data, SerializerFeature.PrettyFormat)
    }

    fun render(): String {

        val sb = StringBuilder()
        for (node in document.children) {
            sb.append(node.chars).append(BasedSequence.EOL_CHARS)
        }

        return sb.toString()
    }
}