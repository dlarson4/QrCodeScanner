package com.example.qrcodescanner

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.google.android.gms.vision.barcode.Barcode
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Context
import android.widget.Button
import androidx.core.app.ActivityCompat
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.annotation.NonNull
import android.provider.MediaStore
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.Log
import android.util.SparseArray
import com.google.android.gms.vision.Frame
import java.io.FileNotFoundException
import kotlin.math.min


class MainActivity : AppCompatActivity() {
    companion object {
        private val TAG = MainActivity::class.java.simpleName

        private const val PHOTO_REQUEST = 10
        private const val REQUEST_WRITE_PERMISSION = 20
        private const val SAVED_INSTANCE_URI = "uri"
        private const val SAVED_INSTANCE_RESULT = "result"
    }

    private lateinit var scanResults: TextView
    private lateinit var detector: BarcodeDetector
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scan_barcode_layout)

        val button = findViewById<Button>(R.id.button)
        scanResults = findViewById(R.id.scan_results)

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(SAVED_INSTANCE_URI)) {
                imageUri = Uri.parse(savedInstanceState.getString(SAVED_INSTANCE_URI))
            }
            if (savedInstanceState.containsKey(SAVED_INSTANCE_RESULT)) {
                scanResults.text = savedInstanceState.getString(SAVED_INSTANCE_RESULT)
            }
        }
        button.setOnClickListener {
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_PERMISSION
            )
        }

        detector = BarcodeDetector.Builder(applicationContext)
            .setBarcodeFormats(Barcode.DATA_MATRIX or Barcode.QR_CODE)
            .build()

        if (!detector.isOperational) {
            scanResults.text = getString(R.string.could_not_setup_detector)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            @NonNull permissions: Array<String>,
                                            @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_WRITE_PERMISSION -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePicture()
            } else {
                Toast.makeText(this@MainActivity, "Permission Denied!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Override
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PHOTO_REQUEST && resultCode == RESULT_OK) {
            launchMediaScanIntent()
            try {
                val localImageUri = imageUri
                if (localImageUri == null) {
                    Log.e(TAG, "onActivityResult: imageUri was null")
                    return
                }
                
                val bitmap: Bitmap? = decodeBitmapUri(this, localImageUri)
                
                if (detector.isOperational && bitmap != null) {
                    val frame: Frame = Frame.Builder().setBitmap(bitmap).build()
                    
                    val barcodes: SparseArray<Barcode>  = detector.detect(frame)

                    for (index in 0 until barcodes.size()) {
                        val code: Barcode = barcodes.valueAt(index)

                        scanResults.text = String.format("%s %s\n", scanResults.text, code.displayValue)

                        when (barcodes.valueAt(index).valueFormat) {
                            Barcode.CONTACT_INFO -> {
                                Log.i(TAG, "CONTACT_INFO")
                                Log.i(TAG, code.contactInfo.title)
                            }
                            Barcode.EMAIL -> {
                                Log.i(TAG, "EMAIL")
                                Log.i(TAG, code.email.address)
                            }
                            Barcode.ISBN -> {
                                Log.i(TAG, "ISBN")
                                Log.i(TAG, code.rawValue)
                            }
                            Barcode.PHONE -> {
                                Log.i(TAG, "PHONE")
                                Log.i(TAG, code.phone.number)
                            }
                            Barcode.PRODUCT -> {
                                Log.i(TAG, "PRODUCT")
                                Log.i(TAG, code.rawValue)
                            }
                            Barcode.SMS -> {
                                Log.i(TAG, "SMS ->")
                                Log.i(TAG, code.sms.message)
                            }
                            Barcode.TEXT -> {
                                Log.i(TAG, "TEXT")
                                Log.i(TAG, code.rawValue)
                            }
                            Barcode.URL -> {
                                Log.i(TAG, "URL")
                                Log.i(TAG, "url: " + code.url.url)
                            }
                            Barcode.WIFI -> {
                                Log.i(TAG, "WIFI")
                                Log.i(TAG, code.wifi.ssid)
                            }
                            Barcode.GEO -> {
                                Log.i(TAG, "GEO")
                                Log.i(TAG, "${code.geoPoint.lat} : ${code.geoPoint.lng}")
                            }
                            Barcode.CALENDAR_EVENT -> {
                                Log.i(TAG, "CALENDAR_EVENT")
                                Log.i(TAG, code.calendarEvent.description)
                            }
                            Barcode.DRIVER_LICENSE -> {
                                Log.i(TAG, "DRIVER_LICENSE")
                                Log.i(TAG, code.driverLicense.licenseNumber)
                            }
                            else -> {
                                Log.i(TAG, "unknown")
                                Log.i(TAG, code.rawValue)
                            }
                        }
                    }
                    
                    if (barcodes.size() == 0) {
                        scanResults.setText("Scan Failed: Found nothing to scan")
                    }
                } else {
                    scanResults.setText("Could not set up the detector!")
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load Image", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "", e)
            }
        }
    }

    private fun takePicture() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photo = File(Environment.getExternalStorageDirectory(), "picture.jpg")
        imageUri = FileProvider.getUriForFile(
            this@MainActivity,
            BuildConfig.APPLICATION_ID + ".provider", photo
        )
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        startActivityForResult(intent, PHOTO_REQUEST)
    }

    private fun launchMediaScanIntent() {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = imageUri
        this.sendBroadcast(mediaScanIntent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (imageUri != null) {
            outState.putString(SAVED_INSTANCE_URI, imageUri.toString())
            outState.putString(SAVED_INSTANCE_RESULT, scanResults.text.toString())
        }
        super.onSaveInstanceState(outState)
    }

    @Throws(FileNotFoundException::class)
    private fun decodeBitmapUri(ctx: Context, uri: Uri): Bitmap? {
        val targetW = 600
        val targetH = 600
        val bmOptions = BitmapFactory.Options()
        bmOptions.inJustDecodeBounds = true
        BitmapFactory.decodeStream(ctx.contentResolver.openInputStream(uri), null, bmOptions)
        val photoW = bmOptions.outWidth
        val photoH = bmOptions.outHeight

        val scaleFactor = min(photoW / targetW, photoH / targetH)
        bmOptions.inJustDecodeBounds = false
        bmOptions.inSampleSize = scaleFactor

        return BitmapFactory.decodeStream(
            ctx.contentResolver
                .openInputStream(uri), null, bmOptions
        )
    }
}
