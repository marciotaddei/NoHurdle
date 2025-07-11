package com.example.nohurdle

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import java.io.BufferedReader
import kotlin.math.max

class MainActivity : AppCompatActivity() {

//    private lateinit var rootLayout: View //= findViewById<View>(R.id.root_layout)
//    private lateinit var headerView : LinearLayout
//    private lateinit var boxScroller: ScrollView //= findViewById<ScrollView>(R.id.boxScroller)
//    private lateinit var searchButton: Button
//    private lateinit var resultsScroller: ScrollView //= findViewById<ScrollView>(R.id.resultsScroller)
//    private lateinit var resultsTable: GridLayout
    private lateinit var inputBoxTable: LinearLayout

    private val rows = mutableListOf<List<EditText>>() // Store 2D list of rows
    val coloredBoxes = listOf(R.drawable.box_wrong, //R.drawable.box_default,
                        R.drawable.box_elsewhere, R.drawable.box_correct )
    val cellColorMap = mutableMapOf<EditText, Int>()
    private var boxTextSize = 40f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.i("LayoutDebug", "Inflated layout: "+R.layout.activity_main.toString())

        val iconButton = findViewById<ImageButton>(R.id.iconButton)
        val aboutButton = findViewById<ImageButton>(R.id.aboutButton)
        inputBoxTable = findViewById<LinearLayout>(R.id.inputBoxes)
        val searchButton  = findViewById<Button>(R.id.findWords)
        val resetButton = findViewById<ImageButton>(R.id.resetButton)
        val resultsTable  = findViewById<GridLayout>(R.id.resultsTable)
//        rootLayout = findViewById<View>(R.id.root_layout)
//        headerView = findViewById<LinearLayout>(R.id.header)
//        boxScroller = findViewById<ScrollView>(R.id.boxScroller)
//        resultsScroller = findViewById<ScrollView>(R.id.resultsScroller)
        boxTextSize = resources.displayMetrics.widthPixels*.04f

        addNewRow()
        attachListenersToAllBoxes()

        iconButton.setOnClickListener {
            // 1) Simple message-only dialog
            AlertDialog.Builder(this)
                .setTitle("Icon attribution")
                .setMessage("Icon from Hurdle 27600 by Desbenoit "+
                            "from thenounproject.com (CC BY 3.0)")
                .setPositiveButton("OK", null)
                .show()
        }

        aboutButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Help/About")
                .setMessage(
                    "The NoHurdle app helps you with Wordle-like problems. " +
                    "Type in the word(s) you have input, and set the color "+
                    "the game has shown for each letter, either by tapping "+
                    "repeatedly or long-pressing for a pop-up. "+
                    "Defaults to gray if color is not set.\n\n"+
                     
                    "Press the \"find words\" button to see the candidates. "+
                    "You can click a candidate below to add it to the words above.\n\n"+

                    "The app narrows down the possible words, but it doesn't " +
                    "know the answer for today's problem " +
                    "(that would make the game completely trivial).\n\n" +
                      
                    "App icon based on Hurdle 27600 by Desbenoit from thenounproject.com (CC BY 3.0)"
                )
                .setPositiveButton("OK", null)
                .show()
        }

        resetButton.setOnClickListener {
            resultsTable.removeAllViews()
            inputBoxTable.removeAllViews()
            rows.clear()
            addNewRow()
            attachListenersToAllBoxes()
        }

        searchButton.setOnClickListener {
            resultsTable.removeAllViews()
            deleteEmptyRows()

            //set box_default to box_wrong
            rows.forEach{row-> row.forEach { cell ->
                if(!cell.text.isEmpty() && cellColorMap[cell]==0)
                    {cell.setBackgroundResource(coloredBoxes[0])}
                }
            }

            var currentKnowledge = rows.map{row->
            row.map{Pair(it.text.firstOrNull()?.lowercaseChar(), cellColorMap[it]!!)}}
            if(currentKnowledge.all{row -> row.all{it.first==null}})
                return@setOnClickListener

            var theToast: Toast? = null
            theToast = Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT)
            theToast?.show()

            listOf<String>("precise_wordled.txt","mega_wordled.txt","gigantic_wordled.txt").
                    forEachIndexed { i, fileName ->
                        val wordledDict =
                            BufferedReader(assets.open(fileName).reader())
                                .readLines()
                        val matches = findMatches(currentKnowledge, wordledDict)
                        printWords(matches, resultsTable, i)}

            theToast?.cancel()
            theToast = null

        }
    }

    private fun addNewRow() {
        val numBoxesPerRow = 5
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {setMargins(0, 16, 0, 0)}
        }

        val rowEditTexts = mutableListOf<EditText>()
        repeat(numBoxesPerRow) {
            val editText = AppCompatEditText(this).apply {
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
                setTextColor(getColor(R.color.white))
                textSize = boxTextSize//40f
                Log.d("Box Text Size", this.textSize.toString())
                //typeface = Typeface.DEFAULT_BOLD //set through theme roboto_bold
                imeOptions = EditorInfo.IME_ACTION_NEXT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    {importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO}
                isLongClickable = true
                setBackgroundResource(R.drawable.box_default)
                cellColorMap[this] = 0
                //the colors have no inter-cell relation, so create the listener here
                setOnClickListener {
                    val nextIndex = ((cellColorMap[this] ?: 0) + 1) % coloredBoxes.size
                    setBackgroundResource(coloredBoxes[nextIndex])
                    cellColorMap[this] = nextIndex}
                //Pop-up on long click

                setOnLongClickListener{cell ->
                    @SuppressLint("InflateParams")
                    val popupView = LayoutInflater.from(context)
                        .inflate(R.layout.colors_popup, null, false)

                    val popupWindow = PopupWindow(popupView,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true)

                    listOf(R.id.boxGray, R.id.boxYellow, R.id.boxGreen)
                        .forEach{box->
                            popupView.findViewById<ImageButton>(box).
                            apply{updateLayoutParams<LinearLayout.LayoutParams>{
                                height = cell.width*2/3
                                width  = cell.width*2/3}
                            }
                        }

                    //calculating the offset to center the popup to cell
                    popupWindow.showAsDropDown(cell,
                        (popupWindow.contentView.measuredWidth-cell.width)/2,0)

                    popupView.findViewById<ImageButton>(R.id.boxGray)
                        .setOnClickListener{
                            (cell as EditText).setBackgroundResource(coloredBoxes[0])
                            cellColorMap[cell] = 0
                            popupWindow.dismiss()}
                    popupView.findViewById<ImageButton>(R.id.boxYellow)
                        .setOnClickListener{
                            (cell as EditText).setBackgroundResource(coloredBoxes[1])
                            cellColorMap[cell] = 1
                            popupWindow.dismiss()}
                    popupView.findViewById<ImageButton>(R.id.boxGreen)
                        .setOnClickListener{
                            (cell as EditText).setBackgroundResource(coloredBoxes[2])
                            cellColorMap[cell] = 2
                            popupWindow.dismiss()}
                    true
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
                            if (row.all { it.text.length == 1 } && rowIndex == rows.lastIndex ) {
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

    private fun deleteEmptyRows() {//except last row
        for (i in rows.size - 2 downTo 0) {
            val rowEditTexts = rows[i]
            if (rowEditTexts.all { it.text.isNullOrBlank() }) {
                val rowLayout = rowEditTexts.first().parent as? LinearLayout
                rowLayout?.let { inputBoxTable.removeView(it) }
                rows.removeAt(i)
                attachListenersToAllBoxes()
            }
        }
    }

    private fun moveToNextInput(rowIndex: Int, colIndex: Int) {
        if (rowIndex >= rows.size) return
        val currentRow = rows[rowIndex]
        // Move to next in same row
        if (colIndex < currentRow.lastIndex) {currentRow[colIndex + 1].requestFocus()}
        // Move to first in next row
        else if (rowIndex < rows.lastIndex) {rows[rowIndex + 1][0].requestFocus()}
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
                if (letter==null || status==-1) return@forEachIndexed
                else if (status==0){
                    letterPosOut.putIfAbsent(letter, mutableListOf())
                    letterPosOut[letter]!!.add(i)
                    howManyEach[letter] = Pair(howManyEach[letter]?.first ?:0, true) //if not present, default to lower bound of 0
                }
                else if (status==1){
                    letterPosOut.putIfAbsent(letter, mutableListOf())
                    letterPosOut[letter]!!.add(i)
                    howManyEach[letter] = Pair((howManyEach[letter]?.first ?:0) +1,
                                            howManyEach[letter]?.second == true ) //not redundant because of null
                }
                if (status==2) {
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

        Log.d("Constraints", "Letters in: " + lettersIn.toString()
                + ", letters out: " + lettersOut.toString()
                +", pos in: " + letterPosIn.toString()
                + ", pos not: " + letterPosOut.toString())


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

    private fun printWords(matches: List<String>, resultsTable: GridLayout, category: Int){
        val cellWidth = resources.displayMetrics.widthPixels/resultsTable.columnCount
        val paint = Paint().apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 100f} // Arbitrary large size for scale factor
        val maxTextSizePx = 100f * (cellWidth * 0.9f / paint.measureText("MMMMM"))

        //variable for background color and text on how likely
        val likelihoodColor = when(category){
            0->Pair("likely",ContextCompat.getColor(this, R.color.light_green))
            1->Pair("less likely",ContextCompat.getColor(this, R.color.light_yellow))
            2->Pair("unlikely",ContextCompat.getColor(this, R.color.light_red))
            else->Pair("unknown-status",ContextCompat.getColor(this, R.color.light_gray))}

        val string = likelihoodColor.first +" words: " + matches.size.toString()
        val header = AppCompatTextView(this).apply {
            text = string
            setTextColor(ContextCompat.getColor(context,R.color.white))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD //set via roboto_bold font
            layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),  // current row
                GridLayout.spec(0, 4)  // span 4 columns
            ).apply { width = GridLayout.LayoutParams.MATCH_PARENT }
        }
        resultsTable.addView(header)

        for (word in matches) {
            val textView = AppCompatTextView(this).apply {
                text = word.uppercase()
                typeface = Typeface.DEFAULT_BOLD //set via roboto_bold font
                setPadding(0, 0, 0, 0)
                setTextSize(TypedValue.COMPLEX_UNIT_PX,maxTextSizePx)
                setTextColor(ContextCompat.getColor(context, R.color.black))
                gravity = Gravity.CENTER
                setBackgroundColor(likelihoodColor.second)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                isClickable = true
                tag = word
                setOnClickListener { v -> addWordToBoxes(v.tag as String) }
            }
            resultsTable.addView(textView)
        }
    }

    private fun addWordToBoxes(word: String){
        //add new row if last one not empty
        if(rows.last().any{!it.text.isNullOrBlank()})
            {addNewRow(); attachListenersToAllBoxes()}
        rows.last().forEachIndexed { i, box ->
            box.setText(word[i].uppercaseChar().toString())
        }
    }
}

