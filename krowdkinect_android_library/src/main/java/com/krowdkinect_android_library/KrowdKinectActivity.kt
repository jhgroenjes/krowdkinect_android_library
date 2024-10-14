@file:OptIn(ExperimentalUnsignedTypes::class)  // suppresses warning for using Unsigned Integers (new to android)


package com.krowdkinect_android_library

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.TextView
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.icu.util.Calendar
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
//import androidx.appcompat.app.AppCompatActivity
import io.ably.lib.realtime.*
import io.ably.lib.types.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.GregorianCalendar
import kotlin.random.Random


// pixelArray 16-bit Variables
var seed: UShort = 0u
var masterRows: UShort = 0u
var masterCols: UShort = 0u
var screenRows: UShort = 0u
var screenCols: UShort = 0u
var calcPacketsRemain : UShort = 0u
var startPixel :UShort = 0u
var endPixel : UShort = 0u
var BPM: UShort = 120u // 120 beats per min default

// featuresArray
var surfaceR : Int = 255
var surfaceG : Int = 0
var surfaceB : Int = 0
var flashlightStatus : UByte = 0u
var white2Flash : UByte = 0u
var motionTrigger : UByte = 0u
var homeAwayZone : UByte = 0u
var randomClientStrobe : UByte = 0u
var audioSync : UByte = 0u
var feature4 : UByte = 0u
var feature5 : UByte = 0u
var ablyDisconnect : UByte = 0u
var red : Int = 255
var green : Int = 0
var blue : Int = 0
var vDevID: UInt = 0u
var torchBrightness : Int = 100
var homeAwaySent = "All"
val zoneItems = arrayOf("All", "Home", "Away")
const val appVersion = "Ver. 0.4.0"
const val pixelArrayBytes = 18
const val featuresArrayBytes = 14
var screenPixel = false


// *** Arrays ***
var pixelArray = UShortArray(9)  //  <-- Defined down below in that section because it's use is OptIn right now
var featuresArray = UByteArray(14)
//var colorArray = set below because of variable size based on received packet.   Ui84rg.53iies:replacewithyourkey

//  Set by the Host App in the SDK version, but these are the DEFAULTS
var ablyKey = "Hf22Ud.5U32zw:vnbLv44ureyfhgr0Sgwb2ECgFCSXHAXQomrJOvwp-qk"  // error checking using a proper format, future
var deviceID: UInt = 1u
var displayName = ""
var displayTagline = ""
var homeAwayHide = false
var seatNumberEditHide = false
var homeAwaySelection = "All"

//Android Specific variables for KrowdKinect (not used in Xcode implementation)
var isFlashOn: Boolean = false
private val handler = Handler(Looper.myLooper()!!)
var randomBrightness : Float = 0.7f
var torchId: String? = null  //used for candle mode funcitons
var isProcessRunning = true  //used for candle mode funcitons
private lateinit var cameraManager: CameraManager   //used for candle mode funcitons


class KrowdKinectActivity : Activity() {
    //define the ably read-only API key and other channel values.
    private lateinit var options: ClientOptions
    private lateinit var ably: AblyRealtime
    private lateinit var channel: Channel

    //audio player init.
    private var mediaPlayer: MediaPlayer? = null

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_krowdkinect)

        // Retrieve KKOptions from the intent and update from Defaults if needed
        val apiKey = intent.getStringExtra("apiKey") ?: ablyKey // Use default if not found

        // Initialize AblyRealtime with the updated ablyKey
        options = ClientOptions(apiKey)
        ably = AblyRealtime(options)
        channel = ably.channels.get("KrowdKinect")

        // set the deviceID from the intent
        deviceID = intent.getIntExtra("deviceID", 1).toUInt()

        if (intent.getStringExtra("displayName") != displayName) {
            displayName = intent.getStringExtra("displayName").toString()
        }
        if (intent.getStringExtra("displayTagline") != displayTagline) {
            displayTagline = intent.getStringExtra("displayTagline").toString()
        }
        if (intent.getBooleanExtra("homeAwayHide", true) != homeAwayHide) {
            homeAwayHide = intent.getBooleanExtra("homeAwayHide", true)
        }
        if (intent.getBooleanExtra("seatNumberEditHide", true) != seatNumberEditHide) {
            seatNumberEditHide = intent.getBooleanExtra("seatNumberEditHide", true)
        }
        if (intent.getStringExtra("homeAwaySelection") != homeAwaySelection) {
            homeAwaySelection = intent.getStringExtra("homeAwaySelection").toString()
        }

        // Handle the "X" button to close the screen
         val exitButton: ImageView = findViewById(R.id.exitButton)
         exitButton.setOnClickListener {
             showExitConfirmationDialog()
         }


        //  code to initialize the camera manager and set the torch ID needed for candle mode
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasTorch =
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) as? Boolean
                if (hasTorch == true) {
                    torchId = id
                    break
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        // function to show the picklist dialog for the zone selection
        fun showPickListDialog(zoneItems: Array<String>) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Pick your seating zone:")
                .setItems(zoneItems) { _: DialogInterface, i: Int ->
                    // This is called when an item is selected
                    val selectedOption = zoneItems[i]
                    println(selectedOption)
                    // Do something with the selected option, for example, set it to a variable
                    homeAwaySelection = selectedOption
                    val buttonTxt = findViewById<TextView>(R.id.pickListButton)
                    buttonTxt.text = homeAwaySelection
                }
            val dialog = builder.create()
            dialog.show()
        }

        //setting an initial background color (black) and brightness and other stuff
        window.decorView.setBackgroundColor(Color.parseColor("#000000"))
        val brightness = window.attributes
        brightness.screenBrightness = 1.0f
        window.attributes = brightness
        val kKTxt = findViewById<TextView>(R.id.KrowdKinectText)
        kKTxt.text = displayName
        val bTSTxt = findViewById<TextView>(R.id.BeTheShowText)
        bTSTxt.text = displayTagline
        val vrTxt = findViewById<TextView>(R.id.versionText)
        vrTxt.text = appVersion
        val seatTxt = findViewById<TextView>(R.id.seatText)
        val seatEditTxt = findViewById<EditText>(R.id.etSeatNumber)
        val zoneTxt = findViewById<TextView>(R.id.zoneText)
        //val connectedTxt = findViewById<TextView>(R.id.connectedText)
        val buttonTxt = findViewById<TextView>(R.id.pickListButton)

        // Control Seat visibility based on seatNumberEditHide
        if (seatNumberEditHide) {
            seatTxt.visibility = View.GONE    // Hide the Seat label
            seatEditTxt.visibility = View.GONE  // Hide the Seat EditText input field
        } else {
            seatTxt.visibility = View.VISIBLE  // Show the Seat label
            seatEditTxt.visibility = View.VISIBLE  // Show the Seat EditText input field
        }

        // Control Zone visibility based on homeAwayHide
        if (homeAwayHide) {
            zoneTxt.visibility = View.GONE    // Hide the Zone label
            buttonTxt.visibility = View.GONE  // Hide the Zone picklist button
        } else {
            zoneTxt.visibility = View.VISIBLE  // Show the Zone label
            buttonTxt.visibility = View.VISIBLE  // Show the Zone picklist button
        }

        buttonTxt.setBackgroundColor(Color.TRANSPARENT)
        buttonTxt.text = homeAwaySelection
        //println(homeAwaySelection)
        kKTxt.setTextColor(Color.parseColor("#FFFFFF"))
        bTSTxt.setTextColor(Color.parseColor("#FFFFFF"))
        vrTxt.setTextColor(Color.parseColor("#FFFFFF"))
        seatTxt.setTextColor(Color.parseColor("#FFFFFF"))
        seatEditTxt.setTextColor(Color.parseColor("#FFFFFF"))
        zoneTxt.setTextColor(Color.parseColor("#FFFFFF"))
        buttonTxt.setTextColor(Color.parseColor("#FFFFFF"))

        // code to display and capture the Zone from the user
        buttonTxt.setOnClickListener {
            showPickListDialog(zoneItems)
        }

        seatEditTxt.setText(deviceID.toString())
        window.statusBarColor = this.getColor(R.color.black)

        //keep the phone from sleeping and dimming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        //monitor the edittext "enter" status and updated the deviceID when Enter is pressed.
        seatEditTxt.setOnEditorActionListener { _, keyCode, event ->
            if (((event?.action ?: -1) == KeyEvent.ACTION_DOWN)
                || keyCode == EditorInfo.IME_ACTION_DONE
            ) {
                deviceID = seatEditTxt.text.toString().toUInt()
                //println("DeviceID is now  $deviceID")
                //dismiss the keyboard now and clear the edittext focus.
                val view: View? = this.currentFocus
                val inputMethodManager =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
                seatEditTxt.clearFocus()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        //Join a WebSocket Channel on Ably
        ably.connection.on(ConnectionState.connected) { state ->
            //println("New state is " + state.current.name)
            if (state.current == ConnectionState.connected) {
                // Successful connection
                ablyindicator(true)
                println("OK-ably!")
            } else if (state.current == ConnectionState.failed) {
                println("Connection to Ably Failed")
            }
        }
        //Connect to the ably channel so that the message listener gets started.
        // this subscribe scope goes all the way down through all the receive handler parts

        //  ******************************************************************************
        //  *************************  R E C E I V E    H A N D L E R  *******************
        //  ******************************************************************************

        channel.subscribe { message ->
            val data: ByteArray? = message.data as? ByteArray
            //*** Immediately do a check to see if the "extras" channel tag from ably's websocket message is empty.
            //  if it's NOT, that means a website message came in, so we just pick a random color for demonstration.
            if (message.extras != null) {
                println("DEMO Message received!")
                surfaceR = Random.nextInt(256)
                surfaceG = Random.nextInt(256)
                surfaceB = Random.nextInt(256)
                val color = intArrayOf(surfaceR, surfaceG, surfaceB)
                runOnUiThread { setBackgroundColor(color) }
            } else {
                //----------------------------------------------
                //------      R E S E T    A L L      ----------
                //----------------------------------------------`
                vDevID = 0u
                screenPixel = false
                // Break loops from the previos packet
                handler.removeCallbacksAndMessages(null)
                //stop the candle process too in new-packet receive
                stopTorchControlProcess()


                //--------------------------------------------------------------
                //------   pixelArray and featuresArray Bits Parsed   ----------
                //--------------------------------------------------------------
                val littleEndian = ByteOrder.LITTLE_ENDIAN
                val buffer = ByteBuffer.wrap(data).order(littleEndian)
                // Extract the 16-bit values to pixelArray
                for (i in 0 until pixelArrayBytes / 2) {
                    pixelArray[i] = buffer.short.toUShort()
                }
                println(pixelArray)
                // Extract the 8-bit values to featuresArray
                for (i in 0 until featuresArrayBytes) {
                    val value = buffer.get().toUByte().toInt()
                    featuresArray[i] = if (value >= 0) value.toUByte() else (256 + value).toUByte()
                    // println (featuresArray[i])
                }
                println(featuresArray)

                //  Extract the remaining data it 8-bit RGB values into colorArray
                val remainingDataBytes = data!!.size - pixelArrayBytes - featuresArrayBytes
                val numberColorSets = remainingDataBytes / 3   //each r, g, b is a byte
                val colorArray = Array(numberColorSets) { IntArray(3) }
                for (i in 0 until numberColorSets) {
                    red = buffer.get().toUByte().toInt()
                    colorArray[i][0] = red and 0xFF
                    green = buffer.get().toUByte().toInt()
                    colorArray[i][1] = green and 0xFF
                    blue = buffer.get().toUByte().toInt()
                    colorArray[i][2] = blue and 0xFF
                    // println("colorArray r g b's are: $red $green $blue ")
                }

                //----------------------------------------------------
                //------------   Determine Zone First   --------------
                //----------------------------------------------------
                if (featuresArray[8].toInt() == 0) {
                    homeAwaySent = "All"
                }
                if (featuresArray[8].toInt() == 1) {
                    homeAwaySent = "Home"
                }
                if (featuresArray[8].toInt() == 2) {
                    homeAwaySent = "Away"
                }

                if (homeAwaySent == "All" || homeAwaySelection == homeAwaySent) {
                    // do everything  below in Receive Handler.    else nothing.

                    //----------------------------------------------------
                    //------   Set Screen/Torch Brightness [3]  ----------
                    //----------------------------------------------------
                    // run the functions to see if either of these features were set.
                    randomBrightnessOn() // stop the looping if featuresArray[7] == 0 or start if == 2
                    randomColorFlashOn() // start/stop the random color flashing featuresArray[7] == 1
                    // torchIntensity(torchBrightness)
                    val torchValue = featuresArray[3].toInt()
                    if (torchValue in 1..10) {
                        torchBrightness = torchValue * 10
                        runOnUiThread {
                            setScreenBrightness(torchValue / 10.0f)
                        }
                    }

                    // if motion trigger was sent to flicker the candle do that here.
                    if (motionTrigger.toInt() == 4) {
                        startTorchControlProcess()
                    } else {
                        stopTorchControlProcess()
                    }

                    //----------------------------------------------
                    //------   AUDIO Playback Features   -----------
                    //----------------------------------------------
                    val soundMapping = mapOf(
                        1 to Pair(R.raw.police, 0.2F),
                        2 to Pair(R.raw.airraidsiren, 0.2F),
                        3 to Pair(R.raw.audience, 0.2F),
                        4 to Pair(R.raw.rain, 0.2F),
                        5 to Pair(R.raw.wolf, 0.2F),
                        6 to Pair(R.raw.fire, 0.2F),
                        7 to Pair(R.raw.wind, 0.2F),
                        8 to Pair(R.raw.metronome, 0.2F),
                        84 to Pair(R.raw.police, 0.6F),
                        85 to Pair(R.raw.airraidsiren, 0.6F),
                        86 to Pair(R.raw.audience, 0.6F),
                        87 to Pair(R.raw.rain, 0.6F),
                        88 to Pair(R.raw.wolf, 0.6F),
                        89 to Pair(R.raw.fire, 0.6F),
                        90 to Pair(R.raw.wind, 0.6F),
                        91 to Pair(R.raw.metronome, 0.6F),
                        167 to Pair(R.raw.police, 1.0F),
                        168 to Pair(R.raw.airraidsiren, 1.0F),
                        169 to Pair(R.raw.audience, 1.0F),
                        170 to Pair(R.raw.rain, 1.0F),
                        171 to Pair(R.raw.wolf, 1.0F),
                        172 to Pair(R.raw.fire, 1.0F),
                        173 to Pair(R.raw.wind, 1.0F),
                        174 to Pair(R.raw.metronome, 1.0F)
                    )
                    val featureValue = featuresArray[6].toInt()
                    //println("Audio Playback of #: $featureValue ")
                    runOnUiThread {
                        when (featureValue) {
                            254 -> {
                                mediaPlayer?.stop()
                            }

                            else -> {
                                //----------------------------------------------------
                                //----------   Check for Synced playback  ------------
                                //----------------------------------------------------

                                val calendar = GregorianCalendar()
                                if (featuresArray[10].toInt() == 255 || featuresArray[10].toInt() == 254) {
                                    // Play the audio if the features array says so and the 5 second interval has been reached.
                                    val currentTime = calendar[Calendar.SECOND]
                                    // ---------------------------------------
                                    //             ADJUSTMENT VARIABLE
                                    val adjustment =
                                        -400  //Android phones always seem to lag iPhones
                                    // ---------------------------------------
                                    val delay = (5000 - (currentTime % 5000)) + adjustment
                                    GlobalScope.launch {
                                        delay(delay.toLong())
                                        PlayAudio(soundMapping, featureValue)
                                    }

                                    //----------------------------------------------------
                                    //-------------   Un-synced Playback  ----------------
                                    //----------------------------------------------------
                                } else {
                                    PlayAudio(
                                        soundMapping,
                                        featureValue
                                    )
                                }
                            }
                        }
                    }

                    //----------------------------------------------
                    //------   Assign pixelArray Array  ----------
                    //----------------------------------------------
                    seed = pixelArray[0]
                    masterRows = pixelArray[1]
                    masterCols = pixelArray[2]
                    screenRows = pixelArray[3]
                    screenCols = pixelArray[4]
                    calcPacketsRemain = pixelArray[5]
                    startPixel = pixelArray[6]
                    endPixel = pixelArray[7]
                    BPM = pixelArray[8]

                    //----------------------------------------------
                    //------   Assign featruesArray Array  ----------
                    //----------------------------------------------
                    surfaceR = featuresArray[0].toInt()
                    surfaceG = featuresArray[1].toInt()
                    surfaceB = featuresArray[2].toInt()
                    //println("Surface Color is: $surfaceR, $surfaceG, $surfaceB ")
                    flashlightStatus = featuresArray[4]
                    white2Flash = featuresArray[5]
                    motionTrigger = featuresArray[7]
                    homeAwayZone = featuresArray[8]
                    randomClientStrobe = featuresArray[9]
                    audioSync = featuresArray[10]
                    feature4 = featuresArray[11]
                    feature5 = featuresArray[12]
                    ablyDisconnect = featuresArray[13]


                    //----------------------------------------------
                    //------     Surface or Screen?       ----------
                    //----------------------------------------------
                    //Run the screen Pixel logic to determine if This DeviceID is in the screen or not:
                    for (counter in 0 until screenRows.toInt()) {
                        if (deviceID >= seed && deviceID <= seed + screenCols - 1u) {
                            //if above is TRUE, this is a pixel in the crowd Screen, not surface. Get its VDevID...
                            val additive = screenCols * counter.toUInt()
                            vDevID = deviceID - seed.toUInt() + 1u + additive
                            screenPixel = true
                        } // end if
                        seed = (seed + masterCols).toUShort()
                    } //end FOR LOOP for running through screen pixels

                    //----------------------------------------------
                    //------ Loop through Screen Packets  ----------
                    //----------------------------------------------
                    for (packetCounter in 0..calcPacketsRemain.toInt()) {
                        //println("PacketCounter is: $packetCounter")
                        if (vDevID >= pixelArray[6] && vDevID <= pixelArray[7]) {
                            if (screenPixel) {
                                val arrayIndex = vDevID - startPixel
                                red = colorArray[arrayIndex.toInt()][0]
                                green = colorArray[arrayIndex.toInt()][1]
                                blue = colorArray[arrayIndex.toInt()][2]
                                val color = intArrayOf(red, green, blue)
                                runOnUiThread { setBackgroundColor(color) }
                            } // end Inner IF
                        }  //end Outer If
                    }  //end For loop through the packets

                    //---------------------------------------------------
                    //------ Motion FX [7] = 6 or 7 screen flickr   -----
                    //---------------------------------------------------

                    val screenPixelInt = if (screenPixel) {
                        6
                    } else {
                        5
                    }
                    val arrayIndex = vDevID - startPixel
                    val color = if (screenPixel) {
                        red = colorArray[arrayIndex.toInt()][0]
                        green = colorArray[arrayIndex.toInt()][1]
                        blue = colorArray[arrayIndex.toInt()][2]
                        intArrayOf(red, green, blue)
                    } else {
                        surfaceR = featuresArray[0].toInt()
                        surfaceG = featuresArray[1].toInt()
                        surfaceB = featuresArray[2].toInt()
                        intArrayOf(surfaceR, surfaceG, surfaceB)
                    }
                    specifiedColorFlash(true, color, screenPixelInt)

                    //----------------------------------------------
                    //------   Flashlight MODE Features   ----------
                    //----------------------------------------------

                    // Check to see if rgb-to-Torch MODE is set and light torch instead when color is 255, 255, 255
                    // if set true, the screen will continue to take on the color as normal, but torch will turn on too.
                    if (featuresArray[5].toInt() == 255 && red == 255 && green == 255 && blue == 255) {
                        // update flashlightStatus so it stays on.
                        flashlightStatus = 2u   /* turn on */
                        featuresArray[4] = 2u
                        flashlightOn(torchBrightness)
                    }
                    //Torch On
                    if (featuresArray[4].toInt() == 2) {
                        flashlightOn(torchBrightness)
                    } //end if

                    //Torch Reset to OFF
                    if (featuresArray[4].toInt() == 1) {
                        val packageManager = this.packageManager
                        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                            val cameraManager =
                                this.getSystemService(CAMERA_SERVICE) as CameraManager
                            val cameraId = cameraManager.cameraIdList[0]
                            try {
                                cameraManager.setTorchMode(cameraId, false)
                                isFlashOn = false
                            } catch (e: CameraAccessException) {
                                e.printStackTrace()
                            }
                        }
                    }   // end Torch Reset to OFF if

                    // Flashlight Strobe and Random Selection
                    if (featuresArray[9].toInt() != 255) {
                        if (featuresArray[4].toInt() >= 3 && featuresArray[4].toInt() <= 27) {
                            var flashCount = 0
                            val secondsRate = 30.00000
                            val flashRate = (secondsRate / BPM.toDouble()) * 1000
                            for (i in 1..(featuresArray[4].toInt() - 2) * 2) {
                                toggleFlashlight(torchBrightness)
                                flashCount++
                                if (flashCount < (featuresArray[4].toInt() - 2) * 2) { // flash the right amount of times.
                                    Thread.sleep(flashRate.toLong())
                                    //Looper.postDelayed(this, 500)
                                    // Timer().schedule(2000) {toggleFlashlight()}
                                }
                            }
                            flashlightOff()  // make sure it ends OFF
                        }
                    } else {
                        // pick a number between 1 and 4 and only flash if 1
                        val randomNumber = Random.nextInt(3)
                        println(randomNumber)
                        if (randomNumber == 0) {
                            if (featuresArray[4].toInt() >= 3 && featuresArray[4].toInt() <= 27) {
                                var flashCount = 0
                                val secondsRate = 30.00000
                                val flashRate = (secondsRate / BPM.toDouble()) * 1000
                                for (i in 1..(featuresArray[4].toInt() - 2) * 2) {
                                    toggleFlashlight(torchBrightness)
                                    flashCount++
                                    if (flashCount < (featuresArray[4].toInt() - 2) * 2) { // flash the right amount of times.
                                        Thread.sleep(flashRate.toLong())
                                        //Looper.postDelayed(this, 500)
                                        // Timer().schedule(2000) {toggleFlashlight()}
                                    }
                                }
                                flashlightOff()  // make sure it ends OFF
                            }
                        }
                    }

                    //----------------------------------------------
                    //------   S U R F A C E  Color Set   ----------
                    //----------------------------------------------
                    // Now process features if "screenPixel" didn't get set True
                    if (!screenPixel) {
                        surfaceR = featuresArray[0].toInt()
                        surfaceG = featuresArray[1].toInt()
                        surfaceB = featuresArray[2].toInt()
                        val color = intArrayOf(surfaceR, surfaceG, surfaceB)
                        //window.decorView.setBackgroundColor(color)
                        runOnUiThread { setBackgroundColor(color) }
                    }

                    //---------------------------------------------------
                    //------ Motion FX [7] = 5 or 7 surface flickr   -----
                    //---------------------------------------------------

                    // this is compressed and all handled above

                    //----------------------------------------------------
                    //------  See if Force-Close-Ably was sent  ----------
                    //----------------------------------------------------
                    if (featuresArray[13].toInt() == 255) {
                        ablyindicator(false)
                        channel.unsubscribe()
                        ably.connection.close()
                        ably.connection.on(
                            /* state = */ ConnectionState.closed,
                        ) { state ->
                            println("New state is " + state.current.name)
                            if (state.current == ConnectionState.closed) {
                                // Connection closed
                                println("Closed the connection to KrowdKinect Cloud.")
                                red = 0
                                green = 0
                                blue = 0
                                val color = intArrayOf(red, green, blue)
                                runOnUiThread { setBackgroundColor(color) }
                            } else if (state.current == ConnectionState.failed) {
                                println("Attempt to Close Connection to Ably FAILED")
                            }
                        }
                    } // end ably force-close
                } // end of if Zone sent matched zone on client
            }  // end of Else to check for Demo text message sent or not
        }  // end of the ably receive channel message
    } // end of over ride function On Create




    private fun PlayAudio(
        soundMapping: Map<Int, Pair<Int, Float>>,
        featureValue: Int
    ) {
        soundMapping[featureValue]?.let { (soundRes, volume) ->
            mediaPlayer = MediaPlayer.create(this, soundRes)
            mediaPlayer?.setVolume(volume, volume)
            mediaPlayer?.start()
        }
    }

    //----------------------------------------------
    //------     Additional Functions     ----------
    //----------------------------------------------

    //shared function to actually update the screen color of the device
    private fun setBackgroundColor(rgb: IntArray) {
        val color = Color.rgb(rgb[0], rgb[1], rgb[2])
        window.decorView.setBackgroundColor(color)

        //change the text color on the window so that it's always readable regardless of the background color
        if(color <= -8388608 ){
            val kKTxt = findViewById<TextView>(R.id.KrowdKinectText)
            val bTSTxt = findViewById<TextView>(R.id.BeTheShowText)
            val seatTxt = findViewById<TextView>(R.id.seatText)
            val seatEditTxt = findViewById<EditText>(R.id.etSeatNumber)
            val versionTxt = findViewById<TextView>(R.id.versionText)
            val zoneTxt = findViewById<TextView>(R.id.zoneText)
            val buttonTxt = findViewById<TextView>(R.id.pickListButton)
            kKTxt.setTextColor(Color.parseColor("#FFFFFF"))
            bTSTxt.setTextColor(Color.parseColor("#FFFFFF"))
            seatTxt.setTextColor(Color.parseColor("#FFFFFF"))
            seatEditTxt.setTextColor(Color.parseColor("#FFFFFF"))
            versionTxt.setTextColor(Color.parseColor("#FFFFFF"))
            zoneTxt.setTextColor(Color.parseColor("#FFFFFF"))
            buttonTxt.setTextColor(Color.parseColor("#FFFFFF"))
        }
        else{
            val kKTxt = findViewById<TextView>(R.id.KrowdKinectText)
            val bTSTxt = findViewById<TextView>(R.id.BeTheShowText)
            val seatTxt = findViewById<TextView>(R.id.seatText)
            val seatEditTxt = findViewById<EditText>(R.id.etSeatNumber)
            val versionTxt = findViewById<TextView>(R.id.versionText)
            val zoneTxt = findViewById<TextView>(R.id.zoneText)
            val buttonTxt = findViewById<TextView>(R.id.pickListButton)
            kKTxt.setTextColor(Color.parseColor("#000000"))
            bTSTxt.setTextColor(Color.parseColor("#000000"))
            seatTxt.setTextColor(Color.parseColor("#000000"))
            seatEditTxt.setTextColor(Color.parseColor("#000000"))
            versionTxt.setTextColor(Color.parseColor("#000000"))
            zoneTxt.setTextColor(Color.parseColor("#000000"))
            buttonTxt.setTextColor(Color.parseColor("#000000"))
        }
    }

    //Create a function to handle the changing of color of the connected ably indicator
    private fun ablyindicator(setme: Boolean) {
        val connectedTxtinit = findViewById<TextView>(R.id.connectedText)
        if (setme) {
            // If setme is true, change the indicator LED got to Green
            connectedTxtinit.setTextColor(Color.parseColor("#12FF05"))
            // errorTxt.visibility = View.VISIBLE
        } else {
            connectedTxtinit.setTextColor(Color.parseColor("#FF0405"))
        }
    }

    //create a function called from the packet receive thread to change brightness
    private fun setScreenBrightness(intensity: Float) {
        val lp = window?.attributes
        lp?.screenBrightness = intensity
        window?.attributes = lp
    }

    // flashlight toggle function
    private fun toggleFlashlight(intensity: Int){
        val packageManager = this.packageManager
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            val cameraManager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]

            try {
                isFlashOn = if(!isFlashOn) {

                    //if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    // cameraManager.turnOnTorchWithStrengthLevel(cameraId, intensity)
                    //   println("flashlight intensity code disabled for now")
                    //}
                    cameraManager.setTorchMode(cameraId, true)
                    true
                } else{
                    cameraManager.setTorchMode(cameraId, false)
                    false
                }
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    // flashlight Off Function
    private fun flashlightOff() {
        val packageManager = this.packageManager
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            val cameraManager =
                this.getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            try {
                cameraManager.setTorchMode(cameraId, false)
                isFlashOn = false
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    // flashlight Brightness function
    private fun torchIntensity(intensity: Int){
        val packageManager = this.packageManager
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            //val cameraId = cameraManager.cameraIdList[0]

            try {
                //cameraManager.setTorchMode(cameraId, true)
                if ((android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) && isFlashOn) {
                    //   cameraManager.turnOnTorchWithStrengthLevel(cameraId, intensity)
                    println("flashlight intensity code disabled for now")
                }
                isFlashOn = true
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    // flashlight ON function
    private fun flashlightOn(intensity: Int){
        val packageManager = this.packageManager
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            val cameraManager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    //  cameraManager.turnOnTorchWithStrengthLevel(cameraId, intensity)
                    println("flashlight intensity code disabled for now")
                }
                cameraManager.setTorchMode(cameraId, true)
                isFlashOn = true

            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    // *******************************************
    // *********  Candle Mode Functions  *********
    // *******************************************
    private fun startTorchControlProcess() {
        val handler = Handler(Looper.getMainLooper())
        // Define a runnable to run the torch control process
        val torchControlRunnable = object : Runnable {
            override fun run() {
                if (isProcessRunning) {
                    // Generate random intensity and steps values
                    val newIntensity = Random.nextDouble(0.3, 0.35)
                    val steps = Random.nextInt(1, 21)
                    println(" intensity and steps are:  $newIntensity and $steps")
                    // Update torch intensity
                    updateTorchIntensity(newIntensity)
                    // Change torch intensity by the number of steps
                    changeTorchIntensityBySteps(steps)
                    // Schedule the next run after 1 second
                    handler.postDelayed(this, 10000)
                }
            }
        }
        // Start the torch control process by posting the initial runnable
        handler.post(torchControlRunnable)
    }

    private fun updateTorchIntensity(intensity: Double) {
        try {
            cameraManager.setTorchMode(torchId!!, true)
            // Use intensity value as needed, e.g., for controlling torch brightness
            // stupid android phones can't change the intensity of the torch on API 31 and lower.
            // can't implement this code as I have no way to test it.  my motorola test phone is api 31
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun changeTorchIntensityBySteps(steps: Int) {
        try {
            // Use steps value as needed, e.g., for adjusting torch brightness by steps
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    // Call this method to stop the torch control process
    private fun stopTorchControlProcess() {
        isProcessRunning = false
        try {
            cameraManager.setTorchMode(torchId!!, false)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    // code that should run if the user closes KrowdKinect
    override fun onDestroy() {
        super.onDestroy()
        //Close the Ably WebSockets connection
        //ably's doc say you must Unsubscribe from a channel and Detach from it both.
        channel.unsubscribe()
        ably.connection.close()
        ably.connection.on(ConnectionState.closed, ConnectionStateListener { state ->
            println("New state is " + state.current.name)
            if (state.current == ConnectionState.closed) {
                // Connection closed
                //ablyindicator(false)
                println("Closed the connection to Ably.")
            }
            else if (state.current == ConnectionState.failed) {
                println("Attempt to Close Connection to Ably FAILED")
            }
            // Release the MediaPlayer resources when the activity is destroyed
            mediaPlayer?.release()
            mediaPlayer = null
            stopTorchControlProcess()
        })
        runOnUiThread {setScreenBrightness(0.6f)}
    }

    private fun randomBrightnessOn() {
        if (featuresArray[7].toInt() == 2) {
            randomBrightness = Random.nextDouble(0.0, 1.0).toFloat()
            runOnUiThread {setScreenBrightness(randomBrightness)}
            val delay = (60 * 1000 / BPM.toDouble()).toLong()
            handler.postDelayed({ randomBrightnessOn() }, delay)
        }
    }

    private fun randomColorFlashOn() {
        if (featuresArray[7].toInt() == 1) {
            red = Random.nextInt(256)
            green = Random.nextInt(256)
            blue = Random.nextInt(256)
            val color = intArrayOf(red, green, blue)
            runOnUiThread { setBackgroundColor(color) }
            val delay = (60 * 1000 / BPM.toDouble()).toLong()
            handler.postDelayed({ randomColorFlashOn() }, delay)
        }
    }

    private fun specifiedColorFlash(
        toggle: Boolean,
        color: IntArray,
        screenPixelInt: Int
    ) {
        if ((featuresArray[7].toInt() == screenPixelInt || featuresArray[7].toInt() == 7)) {
            // Set the color to the specified color or black
            if (toggle) {
                // Set the Color to the specified color above ...
                runOnUiThread { setBackgroundColor(color) }
            } else {
                // Set the color to Black
                runOnUiThread { setBackgroundColor(intArrayOf(0, 0, 0)) }
            }
            // delay and recursively call this function
            val delay = 60 * 1000 / BPM.toDouble().toLong()
            handler.postDelayed({ specifiedColorFlash(!toggle, color, screenPixelInt) }, delay)
        }
    }

    // Show dialog to confirm exit
    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Exit")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Yes") { _, _ ->
                finish() // Exit the activity and return to the host app
            }
            .setNegativeButton("No", null)
            .show()
    }
} // end Class
