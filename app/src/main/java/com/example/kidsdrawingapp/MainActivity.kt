package com.example.kidsdrawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null

    var customProgressDialog : Dialog? = null

    val openGalleryLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode == RESULT_OK && result.data != null){
            val imageBackground: ImageView = findViewById(R.id.iv_background)
            imageBackground.setImageURI(result.data?.data)
        }
    }
    // --- FIX ---: Better logic to check if the specific permission was granted.
    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var isGranted = false
            permissions.entries.forEach {
                if (it.value) { // If any of the requested permissions is granted, we are good to go.
                    isGranted = true
                }
            }

            if (isGranted) {
                Toast.makeText(
                    this@MainActivity,
                    "Permission granted, you can now access the gallery.",
                    Toast.LENGTH_LONG
                ).show()

                // --- FIX ---: This is where you will now launch the gallery.
                val pickIntent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)
                // You will need another ActivityResultLauncher to handle the result of picking an image.
                // For now, let's just log that we are here.
                Log.d("MainActivity Intent-ACTION_PICK", "Permission granted. Now launching gallery.")


            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Oops, you denied the permission.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.toFloat())
        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)

        mImageButtonCurrentPaint = linearLayoutPaintColors[2] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
        )

        val ib_brush : ImageButton = findViewById(R.id.ib_brush)
        ib_brush.setOnClickListener {
            showBrushSizeChooserDialog()
        }
        val ibUndo : ImageButton = findViewById(R.id.ib_undo)
        ibUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }
        val ibSave : ImageButton = findViewById(R.id.ib_save)
        ibSave.setOnClickListener {
            // Check if the drawing view is available
            if (drawingView != null) {
                showProgressDialog()
                // Launch a coroutine to handle the bitmap creation and saving in the background
                lifecycleScope.launch {
                    // We need to get the FrameLayout that holds the ImageView and the DrawingView
                    val drawingContainer: FrameLayout = findViewById(R.id.fl_drawing_view_container)

                    // Get the bitmap from the container view
                    val bitmap: Bitmap = getBitmapFromView(drawingContainer)
                    // Call your suspend function to save the file
                    saveBitmapFile(bitmap,false)
                }
            }
        }


        val ibGallery : ImageButton = findViewById(R.id.ib_gallery)
        ibGallery.setOnClickListener {
            requestStoragePermission()
        }
        // --- SHARE ---: 1. Add the click listener for the new Share button
        val ibShare : ImageButton = findViewById(R.id.ib_share)
        ibShare.setOnClickListener {
            if (drawingView != null) {
                showProgressDialog()
                lifecycleScope.launch {
                    val drawingContainer: FrameLayout = findViewById(R.id.fl_drawing_view_container)
                    val bitmap: Bitmap = getBitmapFromView(drawingContainer)
                    // Pass 'true' because we want to trigger the share functionality
                    saveBitmapFile(bitmap, true)
                }
            }
        }



//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }

        // --- START OF THE FIX ---
        // Find the root layout from your XML
        val mainLayout = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            // Get the insets for the system bars (status bar and navigation bar)
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the insets as padding to the view
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            // Return the insets to allow the system to continue processing them
            insets
        }
        // --- END OF THE FIX ---
    }
    private fun showBrushSizeChooserDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")
        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.ib_small_brush)
        smallBtn.setOnClickListener{
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener{
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)
        largeBtn.setOnClickListener{
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view: View){
        if(view !== mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
            )
            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_normal)
            )
            mImageButtonCurrentPaint = view
        }

    }
    private fun isReadStorageAllowed(): Boolean{
        val result = ContextCompat.checkSelfPermission(this,
        Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }


    // --- FIX ---: This function now checks the Android version and requests the correct permission.
    private fun requestStoragePermission() {
        // Define the permissions needed based on the Android version
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13 and above, use READ_MEDIA_IMAGES
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            // For older versions, use READ_EXTERNAL_STORAGE
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissionsToRequest[0])) {
            showRationaleDialog(
                "Kids Drawing App", "Kids Drawing App " +
                        "needs to Access Your Gallery to add an image to draw on."
            )
        } else {
            // Directly ask for the permission.
            requestPermission.launch(permissionsToRequest)
        }
    }
    private fun showRationaleDialog(
        title: String,
        message: String,
    ){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){
                dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun getBitmapFromView(view: View) : Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width,
            view.height,
            Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap

    }
    private suspend fun saveBitmapFile(mBitmap: Bitmap?, shouldShare: Boolean = false): String {        var result = ""
        withContext(Dispatchers.IO){
            if(mBitmap != null){
                try{
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG,90, bytes)
                    val f = File(externalCacheDir?.absoluteFile.toString()
                    + File.separator + "KidsDrawingApp_" + System.currentTimeMillis() / 1000 + ".png")
                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()
                    result = f.absolutePath
                    runOnUiThread{
                        cancelProgressDialog()
                        if(result.isNotEmpty()){
                            // --- SHARE ---: 3. If shouldShare is true, call shareImage
                            if (shouldShare) {
                                shareImage(result)
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "File saved successfully: $result",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else{
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong while saving the file.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                catch (e: Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressDialog() {
        customProgressDialog = Dialog(this@MainActivity)
        /*
        *  Set the screen content from a layout resource .
        *  the resource will be inflated, adding all top level views to the screen
        *  */
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)


        //Start the dialog and display it on screen
        customProgressDialog?.show()
    }
    private fun cancelProgressDialog(){
        if(customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }


    /**
     * Shares the image using a FileProvider to create a secure, shareable content URI.
     * @param result The absolute path of the image file to be shared.
     */
    private fun shareImage(result: String) {
        try {
            // Create a file object from the path returned by saveBitmapFile()
            val file = File(result)

            // Generate a secure URI for the file using the FileProvider.
            // The authority MUST match what you have in AndroidManifest.xml.
            val uri = FileProvider.getUriForFile(
                this,
                "com.example.kidsdrawingapp.fileprovider", // This authority matches your manifest
                file
            )

            // Create a new share intent
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri) // Put the secure URI here
            shareIntent.type = "image/png"

            // Grant temporary read permission to the app that receives the intent
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Start the share activity chooser
            startActivity(Intent.createChooser(shareIntent, "Share Drawing Via"))

        } catch (e: Exception) {
            // Log the error and show a toast if something goes wrong
            Log.e("ShareError", "Error sharing image: ${e.message}")
            Toast.makeText(this, "Sharing failed. Could not create a shareable link for the image.", Toast.LENGTH_LONG).show()
        }
    }


}