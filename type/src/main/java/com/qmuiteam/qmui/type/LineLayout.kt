/*
 * Tencent is pleased to support the open source community by making QMUI_Android available.
 *
 * Copyright (C) 2017-2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the MIT License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qmuiteam.qmui.type

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils.TruncateAt
import com.qmuiteam.qmui.type.element.*
import java.util.*

class LineLayout {
    var maxLines = Int.MAX_VALUE
    var ellipsize: TruncateAt? = null
    var calculateWholeLines = false
    var dropLastIfSpace = true
    var moreText: String? = null
    var moreTextColor = 0
    var moreTextTypeface: Typeface? = null
    var moreUnderlineColor = Color.TRANSPARENT
    var moreBgColor = 0
    var moreUnderlineHeight = 0
    var typeModel: TypeModel? = null
    var shouldHandleWordBreak: Boolean = true

    private var exactlyHeightMaxLine = Int.MAX_VALUE

    private val mLines: MutableList<Line> = ArrayList()

    var totalLineCount = 0
        private set

    val lineCount: Int
        get() {
            return mLines.size
        }

    fun getLine(i: Int): Line? {
        return mLines.getOrNull(i)
    }

    fun measureAndLayout(env: TypeEnvironment, exactlyHeight: Boolean) {
        exactlyHeightMaxLine = Int.MAX_VALUE
        env.clear()
        release()
        if (typeModel == null) {
            return
        }
        var element: Element? = typeModel!!.firstElement()
        var line = Line.acquire()
        var y = 0
        line.init(0, y, env.widthLimit)

        fun addLineAndHandleMaxLineAndNextY(line: Line, isParagraphEndLine: Boolean){
            mLines.add(line)
            if(env.lineHeight != -1){
                y += env.lineHeight.coerceAtLeast(line.contentHeight)
                checkExactlyHeightMaxLine(env, y, exactlyHeight)
                if(isParagraphEndLine){
                    y += env.paragraphSpace
                }
            }else{
                y += line.contentHeight
                checkExactlyHeightMaxLine(env, y, exactlyHeight)
                y += if(isParagraphEndLine){
                    env.paragraphSpace
                }else{
                    env.lineSpace
                }
            }
        }

        while (element != null) {
            element.measure(env)
            if (element is NextParagraphElement) {
                line.add(element)
                line.layout(env, dropLastIfSpace, false)
                addLineAndHandleMaxLineAndNextY(line, true)
                if (canInterrupt()) {
                    handleEllipse(env,true)
                    return
                }
                line = createNewLine(env, y)
            } else if (line.contentWidth + element.measureWidth > env.widthLimit) {
                if (mLines.size == 0 && line.size == 0) {
                    // the width is too small.
                    line.release()
                    return
                }
                val back = line.handleWordBreak(env, shouldHandleWordBreak)
                line.layout(env, dropLastIfSpace, false)
                addLineAndHandleMaxLineAndNextY(line, false)

                if (canInterrupt()) {
                    handleEllipse(env,true)
                    return
                }

                line = createNewLine(env, y)
                if (back != null && back.isNotEmpty()) {
                    for (el in back) {
                        line.add(el)
                    }
                }
                line.add(element)
            } else {
                line.add(element)
            }
            element = element.next
        }
        if (line.size > 0) {
            line.layout(env, dropLastIfSpace, true)
            addLineAndHandleMaxLineAndNextY(line, false)
        } else {
            line.release()
        }
        totalLineCount = mLines.size
        handleEllipse(env,false)
    }

    private fun checkExactlyHeightMaxLine(env: TypeEnvironment, y: Int, exactlyHeight: Boolean){
        if(exactlyHeight && exactlyHeightMaxLine == Int.MAX_VALUE){
            if(y > env.heightLimit){
                exactlyHeightMaxLine = mLines.size - 1
            }else if(y == env.heightLimit){
                exactlyHeightMaxLine = mLines.size
            }
        }
    }

    private fun createNewLine(env: TypeEnvironment, y: Int): Line {
        val line = Line.acquire()
        line.init(0, y, env.widthLimit)
        return line
    }

    private fun handleEllipse(env: TypeEnvironment, fromInterrupt: Boolean) {
        if (mLines.isEmpty() || mLines.size < getUsedMaxLine() || (mLines.size == getUsedMaxLine() && !fromInterrupt)) {
            return
        }
        if (ellipsize == TruncateAt.END) {
            handleEllipseEnd(env)
        } else if (ellipsize == TruncateAt.START) {
            handleEllipseStart(env)
        } else if (ellipsize == TruncateAt.MIDDLE) {
            handleEllipseMiddle(env)
        }
    }

    private fun handleEllipseEnd(env: TypeEnvironment) {
        val maxSize = getUsedMaxLine()
        while (mLines.size > maxSize){
            val line = mLines[mLines.size - 1]
            mLines.remove(line)
            line.release()
        }

        if(mLines.isEmpty()){
            return
        }

        val lastLine = mLines[mLines.size - 1]
        var limitWidth = lastLine.widthLimit
        val ellipseElement: Element = TextElement("...", -1, -1)
        ellipseElement.addSingleEnvironmentUpdater(null, object : EnvironmentUpdater {
            override fun update(env: TypeEnvironment) {
                env.clear()
            }
        })
        ellipseElement.measure(env)
        limitWidth -= ellipseElement.measureWidth
        var moreElement: Element? = null
        if (moreText != null && !moreText!!.isEmpty()) {
            moreElement = MoreTextElement(moreText!!, -1, -1)
            val changeTypes: MutableList<Int> = ArrayList()
            changeTypes.add(TypeEnvironment.TYPE_TEXT_COLOR)
            changeTypes.add(TypeEnvironment.TYPE_BG_COLOR)
            changeTypes.add(TypeEnvironment.TYPE_TYPEFACE)
            changeTypes.add(TypeEnvironment.TYPE_BORDER_BOTTOM_COLOR)
            changeTypes.add(TypeEnvironment.TYPE_BORDER_BOTTOM_WIDTH)
            moreElement.addSingleEnvironmentUpdater(changeTypes, object : EnvironmentUpdater {
                override fun update(env: TypeEnvironment) {
                    if (moreTextColor != 0) {
                        env.textColor = moreTextColor
                    }
                    if (moreBgColor != 0) {
                        env.backgroundColor = moreBgColor
                    }
                    if (moreTextTypeface != null) {
                        env.typeface = moreTextTypeface
                    }
                    if (moreUnderlineHeight > 0) {
                        env.setBorderBottom(moreUnderlineHeight, moreUnderlineColor)
                    }
                }
            })
            moreElement.measure(env)
            limitWidth -= moreElement.measureWidth
        }
        val contentWidth = lastLine.contentWidth
        if (contentWidth < limitWidth) {
            lastLine.restoreVisibleChange()
        } else {
            val elements = lastLine.popAll()
            for (el in elements) {
                if (el.measureWidth <= limitWidth) {
                    lastLine.add(el)
                    limitWidth -= el.measureWidth
                } else {
                    break
                }
            }
        }
        lastLine.add(ellipseElement)
        if (moreElement != null) {
            lastLine.add(moreElement)
        }
        lastLine.layout(env, dropLastIfSpace, true)
        if (moreElement != null) {
            moreElement.x = lastLine.widthLimit - moreElement.measureWidth
        }
    }

    private fun handleEllipseStart(env: TypeEnvironment) {
        env.clear()
        val tmpList = mutableListOf<Line>()
        var tmpY = -1
        val startIndex = mLines.size - getUsedMaxLine()
        val minus = mLines[startIndex].y
        for(i in startIndex until mLines.size){
            mLines[i].y -= minus
            tmpY += mLines[i].contentHeight
            tmpList.add(mLines[i])
        }
        mLines.clear()
        mLines.addAll(tmpList)
        val ellipseElement: Element = TextElement("...", -1, -1)
        ellipseElement.addSingleEnvironmentUpdater(null, object : EnvironmentUpdater {
            override fun update(env: TypeEnvironment) {
                env.clear()
            }
        })
        ellipseElement.measure(env)
        val elements: Queue<Element> = LinkedList()
        elements.add(ellipseElement)
        for (i in mLines.indices) {
            val line = mLines[i]
            val limitWidth = line.widthLimit
            elements.addAll(line.popAll())
            while (!elements.isEmpty()) {
                val el = elements.peek()
                if (el != null) {
                    if (el is NextParagraphElement) {
                        elements.poll()
                        line.add(el)
                        el.move(env)
                        break
                    }
                    if (el is BreakWordLineElement) {
                        elements.poll()
                        continue
                    }
                    if (line.contentWidth + el.measureWidth <= limitWidth) {
                        elements.poll()
                        line.add(el)
                        el.move(env)
                    } else {
                        break
                    }
                } else {
                    elements.poll()
                }
            }
            line.handleWordBreak(env, shouldHandleWordBreak)
            line.layout(env, dropLastIfSpace, false)
            if (elements.isEmpty()) {
                return
            }
        }
    }

    private fun handleEllipseMiddle(env: TypeEnvironment) {
        env.clear()
        val lines: List<Line> = ArrayList(mLines)
        mLines.clear()
        val ellipseElement: Element = TextElement("...", -1, -1)
        ellipseElement.measure(env)
        val maxLines = getUsedMaxLine()
        val ellipseLine = if (maxLines % 2 == 0) maxLines / 2 else (maxLines + 1) / 2
        for (i in 0 until ellipseLine) {
            mLines.add(lines[i])
        }
        val handleLine = lines[ellipseLine - 1]
        val limitWidth = handleLine.widthLimit
        val unHandled: Deque<Element> = LinkedList(handleLine.popAll())
        while (!unHandled.isEmpty()) {
            val el = unHandled.peek()
            if (el != null) {
                if (handleLine.contentWidth + el.measureWidth <= limitWidth / 2f - ellipseElement.measureWidth / 2) {
                    unHandled.poll()
                    handleLine.add(el)
                    el.move(env)
                } else {
                    break
                }
            } else {
                unHandled.poll()
            }
        }
        ellipseElement.measure(env)
        handleLine.add(ellipseElement)
        val nextFullShowLine = lines.size - maxLines + ellipseLine
        var startLine = lines.size - 1
        // find the latest paragraph end line.
        for (i in lines.size - 2 downTo nextFullShowLine + 1) {
            if (lines[i].isMiddleParagraphEndLine) {
                startLine = i
            }
        }
        for (i in ellipseLine..startLine) {
            unHandled.addAll(lines[i].popAll())
        }
        for (i in startLine downTo nextFullShowLine) {
            val line = lines[i]
            while (!unHandled.isEmpty()) {
                val element = unHandled.peekLast()
                if (element != null) {
                    if (element is NextParagraphElement) {
                        unHandled.pollLast()
                        continue
                    }
                    if (element is BreakWordLineElement) {
                        unHandled.pollLast()
                        continue
                    }
                    if (line.contentWidth + element.measureWidth <= line.widthLimit) {
                        unHandled.pollLast()
                        line.addFirst(element)
                    } else {
                        break
                    }
                } else {
                    unHandled.pollLast()
                }
            }
        }
        val toAdd = LinkedList<Element>()
        var toAddWidth = 0
        while (!unHandled.isEmpty()) {
            val element = unHandled.peekLast()
            if (element != null) {
                if (element is NextParagraphElement) {
                    unHandled.pollLast()
                    continue
                }
                if (element is BreakWordLineElement) {
                    unHandled.pollLast()
                    continue
                }
                if (handleLine.contentWidth + toAddWidth + element.measureWidth <= handleLine.widthLimit) {
                    unHandled.pollLast()
                    toAdd.add(0, element)
                    toAddWidth = (toAddWidth + element.measureWidth).toInt()
                } else {
                    break
                }
            } else {
                unHandled.pollLast()
            }
        }
        val firstUnHandle = unHandled.peekFirst()
        val lastUnHandle = unHandled.peekLast()
        var effect = typeModel!!.firstEffect
        if (firstUnHandle != null && lastUnHandle != null) {
            val ellipseEffect: MutableList<Element> = ArrayList()
            while (effect != null && effect.index <= lastUnHandle.index) {
                if (effect.index >= firstUnHandle.index) {
                    ellipseEffect.add(effect)
                }
                effect = effect.next
            }
            if (ellipseEffect.size > 0) {
                val ignoreEffectElement = IgnoreEffectElement(ellipseEffect)
                ignoreEffectElement.move(env)
                handleLine.add(ignoreEffectElement)
            }
        }
        for (el in toAdd) {
            el.move(env)
            handleLine.add(el)
        }
        handleLine.handleWordBreak(env, shouldHandleWordBreak)
        handleLine.layout(env, dropLastIfSpace, ellipseLine == lines.size)
        var lastEnd = handleLine.y + handleLine.contentHeight
        for (i in nextFullShowLine until lines.size) {
            val line = lines[i]
            val prev = lines[i - 1]
            if (prev.isMiddleParagraphEndLine) {
                line.y = lastEnd + env.paragraphSpace.coerceAtLeast(env.lineHeight - handleLine.contentHeight)
            } else {
                line.y = lastEnd + env.lineSpace.coerceAtLeast(env.lineHeight - handleLine.contentHeight)
            }
            lastEnd = line.y + line.contentHeight
            line.move(env)
            line.handleWordBreak(env, shouldHandleWordBreak)
            line.layout(env, dropLastIfSpace, i == lines.size - 1)
            mLines.add(line)
        }
    }

    val maxLayoutWidth: Int
        get() {
            var maxWidth = 0
            for (line in mLines) {
                maxWidth = line.layoutWidth.coerceAtLeast(maxWidth)
            }
            return maxWidth
        }
    val contentHeight: Int
        get() {
            if (mLines.isEmpty()) {
                return 0
            }
            val last = mLines[mLines.size - 1]
            return last.y + last.contentHeight
        }

    fun draw(canvas: Canvas, env: TypeEnvironment) {
        env.clear()
        for (line in mLines) {
            line.draw(env, canvas)
        }
    }

    private fun canInterrupt(): Boolean {
        return mLines.size == getUsedMaxLine() && !calculateWholeLines && (ellipsize == null || ellipsize == TruncateAt.END)
    }

    private fun getUsedMaxLine(): Int {
        return maxLines.coerceAtMost(exactlyHeightMaxLine)
    }

    fun release() {
        for (line in mLines) {
            line.release()
        }
        mLines.clear()
    }
}