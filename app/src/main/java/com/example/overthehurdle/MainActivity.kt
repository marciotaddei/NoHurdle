package com.example.overthehurdle

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.LinearLayout
//import android.widget.TableLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import kotlin.Int
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var inputBoxTable: LinearLayout
    private lateinit var searchButton: Button
    private lateinit var resultsTable: GridLayout
    private val numBoxesPerRow = 5
    private val rows = mutableListOf<List<EditText>>() // Store 2D list of rows
    val coloredBoxes = listOf(
        R.drawable.box_default,
        R.drawable.box_wrong,
        R.drawable.box_elsewhere,
        R.drawable.box_correct )
    val cellColorMap = mutableMapOf<EditText, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputBoxTable = findViewById(R.id.inputBoxes)
        searchButton  = findViewById(R.id.findWords)
        resultsTable  = findViewById(R.id.resultsTable)

        addNewRow()
        attachListenersToAllBoxes()

        searchButton.setOnClickListener {
            Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show()
            val currentKnowledge = rows.map{row->
                row.map{Pair(it.text.firstOrNull()?.lowercaseChar(), cellColorMap[it]!!)}
            }



            val wordledDict =
                BufferedReader(assets.open("precise_wordled.txt").reader())
                    .readLines()
            val matches = findMatches(currentKnowledge, wordledDict)

            printWords(matches, resultsTable)
        }
    }

    private fun addNewRow() {
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {setMargins(0, 16, 0, 0)}
        }

        val rowEditTexts = mutableListOf<EditText>()
        repeat(numBoxesPerRow) {
            val editText = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {setMargins(4, 4, 4, 4)}
                inputType = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                filters = arrayOf(
                    InputFilter.LengthFilter(1),
                    InputFilter {source, _,_,_,_,_ ->
                        if (source.matches(Regex("^[A-Za-z]$")))
                            {source.toString().uppercase()}
                        else ""
                    })
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                textSize = 40f
                typeface = Typeface.DEFAULT_BOLD
                imeOptions = EditorInfo.IME_ACTION_NEXT
                setBackgroundResource(coloredBoxes[0])
                cellColorMap[this] = 0

                //the colors have no inter-cell relation, so create the listener here
                setOnClickListener {
                    val nextIndex = ((cellColorMap[this] ?: 0) + 1) % coloredBoxes.size
                    setBackgroundResource(coloredBoxes[nextIndex])
                    cellColorMap[this] = nextIndex
                }
            }
            rowEditTexts.add(editText)
            rowLayout.addView(editText)
        }

        rows.add(rowEditTexts)
        inputBoxTable.addView(rowLayout)
        rowLayout.post {
            rowEditTexts.forEach { editText ->
                val width = editText.width
                if (width > 0) {
                    editText.layoutParams.height = width
                    editText.requestLayout()
                }
            }
        }
        rows.last().first().requestFocus()
    }

    private fun attachListenersToAllBoxes() {
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { colIndex, editText ->

                editText.setOnEditorActionListener(null)
                editText.setOnKeyListener(null)

                // IME "Next"
                editText.imeOptions = EditorInfo.IME_ACTION_NEXT
                editText.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_NEXT) {
                        moveToNextInput(rowIndex, colIndex)
                        true
                    } else false
                }

                // Character typed
                editText.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        if (s?.length == 1) {
                            moveToNextInput(rowIndex, colIndex)
                            if (row.all { it.text.length == 1 } && isLastRow(rowIndex)) {
                                addNewRow()
                                attachListenersToAllBoxes()
                            }
                        }
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })

                // Backspace
                editText.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN &&
                        keyCode == KeyEvent.KEYCODE_DEL &&
                        editText.text.isEmpty() )
                        {
                        if (colIndex > 0)
                        {rows[rowIndex][colIndex - 1].requestFocus()
                         rows[rowIndex][colIndex - 1].setText("")}
                        else if (rowIndex > 0)
                        {rows[rowIndex - 1].last().requestFocus()
                         rows[rowIndex - 1].last().setText("")}
                        true
                        }
                    else false
                }
            }
        }
    }

    private fun isLastRow(rowIndex: Int): Boolean {
        return rowIndex == rows.lastIndex
    }

    private fun moveToNextInput(rowIndex: Int, colIndex: Int) {
        if (rowIndex >= rows.size) return

        val currentRow = rows[rowIndex]

        // Move to next in same row
        if (colIndex < currentRow.lastIndex) {
            currentRow[colIndex + 1].requestFocus()
        }
        // Move to first in next row
        else if (rowIndex < rows.lastIndex) {
            rows[rowIndex + 1][0].requestFocus()
        }
    }

    private fun findMatches(currentKnowledge: List<List<Pair<Char?,Int>>>,
                            wordledDict:List<String>):List<String>{

        var lettersOut = mutableSetOf<Char>()//letters out
        var lettersIn = mutableMapOf<Char,Pair<Int,Boolean>>() //letters in, with Int many, Boolean for exactly that many (true) or at least (false)
        var letterPosIn = mutableSetOf<Pair<Int,Char>>()
        var letterPosOut = mutableMapOf<Char,MutableList<Int>>() //letters in the word, but not in this position
        for(word in currentKnowledge){
            var howManyEach = mutableMapOf<Char,Pair<Int, Boolean>>() //letter, how many in, if any gray came out
            word.forEachIndexed{ i, (letter, status) ->
                if (letter==null || status==0) return@forEachIndexed
                else if (status==1){
                    letterPosOut.putIfAbsent(letter, mutableListOf())
                    letterPosOut[letter]!!.add(i)
                    howManyEach[letter] = Pair(howManyEach[letter]?.first ?:0, true) //if not present, default to lower bound of 0
                }
                else if (status==2){
                    letterPosOut.putIfAbsent(letter, mutableListOf())
                    letterPosOut[letter]!!.add(i)
                    howManyEach[letter] = Pair((howManyEach[letter]?.first ?:0) +1,
                                            howManyEach[letter]?.second == true ) //not redundant because of null
                }
                if (status==3) {
                    letterPosIn.add(Pair(i,letter))
                    howManyEach[letter] = Pair((howManyEach[letter]?.first ?:0) +1,
                        howManyEach[letter]?.second == true )
                }
            }
            for((letter, pair) in howManyEach){
                lettersIn.putIfAbsent(letter,Pair(0,false))
                lettersIn[letter] = Pair(max(lettersIn[letter]!!.first, pair.first),
                                        lettersIn[letter]!!.second || pair.second)
            }
        }
        for((letter, pair) in lettersIn) {if(pair==Pair(0,true))lettersOut.add(letter)}
        lettersIn.keys.retainAll{ it !in lettersOut}
        letterPosOut.keys.retainAll{ it !in lettersOut}

        Log.d("Constraints", letterPosIn.toString() + lettersIn.toString() +
                letterPosOut.toString()+ lettersOut.toString())

        var possibleWords : List<String> = wordledDict
        possibleWords = possibleWords.filter { word ->
            for( (index, letter) in letterPosIn) {if(word[index]!=letter) return@filter false}
            true}
        possibleWords = possibleWords.filter{word -> (word.all{it !in lettersOut})}
        possibleWords = possibleWords.filter{ word ->
            for((letter, posList) in letterPosOut) {
                for(pos in posList){ if(word[pos]==letter) return@filter false}}
            true}
        possibleWords = possibleWords.filter{word ->
            val wordCharCount = word.groupingBy { it }.eachCount()
            for((letter, countCond) in lettersIn){
                if((wordCharCount[letter]?:0) < countCond.first){return@filter false}
                if((wordCharCount[letter]?:0) > countCond.first && countCond.second)
                    {return@filter false}
                }
            true}

        return possibleWords
    }

    private fun printWords(matches: List<String>, resultsTable: GridLayout){
        resultsTable.removeAllViews()
//        val nCols = resultsTable.columnCount
        val cellWidth = Resources.getSystem().displayMetrics.widthPixels/resultsTable.columnCount
        val paint = Paint().apply {
            typeface = Typeface.DEFAULT_BOLD // Match your actual text style
            textSize = 100f} // Arbitrary large size for scale factor
        val maxTextSizePx = 100f * (cellWidth * 0.9f / paint.measureText("MMMMM"))

        for (word in matches) {
            val textView = TextView(this).apply {
                text = word.uppercase()
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 0)
                setTextSize(TypedValue.COMPLEX_UNIT_PX,maxTextSizePx)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.LTGRAY)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            }
            resultsTable.addView(textView)
        }
    }
}

