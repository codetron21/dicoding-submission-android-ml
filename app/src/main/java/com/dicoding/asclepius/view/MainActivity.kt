package com.dicoding.asclepius.view

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.dicoding.asclepius.R
import com.dicoding.asclepius.databinding.ActivityMainBinding
import com.dicoding.asclepius.helper.ImageClassifierHelper
import com.dicoding.asclepius.helper.getImageUri
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.task.vision.classifier.Classifications
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var imageClassifierHelper: ImageClassifierHelper

    private var currentImageUri: Uri? = null

    private var startBackButtonTime: Long = 0

    private val launcherGallery = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            currentImageUri = uri
            showImage()
        } else {
            Log.d(TAG, "No media selected")
        }

        enableAllButton()
        invalidateOptionsMenu()
    }

    private val editImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == UCrop.RESULT_ERROR) {
                showToast("Failed edit image")
                return@registerForActivityResult
            }

            if (result.resultCode == RESULT_OK) {
                result.data?.let {
                    showToast("Successfully edit image")
                    currentImageUri = UCrop.getOutput(it)
                    showImage()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableAllButton()
        actionListeners()

        imageClassifierHelper = ImageClassifierHelper(
            context = this,
            classifierListener = object : ImageClassifierHelper.ClassifierListener {
                override fun onPreExecute() {
                    runOnUiThread {
                        binding.progressIndicator.isVisible = true
                        disableAllButton()
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        binding.progressIndicator.isVisible = false
                        enableAllButton()
                        showToast(error)
                    }
                }

                override fun onPostExecute(results: List<Classifications>?) {
                    runOnUiThread {
                        Log.d(TAG, results.toString())

                        binding.progressIndicator.isVisible = false
                        enableAllButton()

                        val result = results
                            ?.first()
                            ?.categories
                            ?.first { it.score > 0.5 }

                        moveToResult(result)
                    }
                }
            }
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (menu == null) return false

        if (currentImageUri != null) {
            menu.add(Menu.NONE, MENU_EDIT, Menu.NONE, "Edit Image")
                .setIcon(R.drawable.ic_edit_image)
                .setEnabled(!binding.progressIndicator.isVisible)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }

        menu.add(Menu.NONE, MENU_SAMPLE, Menu.NONE, "Sample")
            .setIcon(R.drawable.ic_sample_image)
            .setEnabled(!binding.progressIndicator.isVisible)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_SAMPLE -> {
                showSampleDialog()
                true
            }

            MENU_EDIT -> {
                runEditImage()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun actionListeners() = with(binding) {
        analyzeButton.setOnClickListener {
            analyzeImage()
        }

        galleryButton.setOnClickListener {
            startGallery()
        }

        onBackPressedDispatcher.addCallback {
            onBackButtonClicked()
        }
    }

    private fun startGallery() {
        launcherGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun showImage() {
        currentImageUri?.let {
            Log.d(TAG, "showImage: $it")
            binding.previewImageView.setImageURI(it)
        }
    }

    private fun analyzeImage() {
        if (currentImageUri == null) return

        imageClassifierHelper.classifyStaticImage(currentImageUri!!)
    }

    private fun moveToResult(data: Category?) {
        val intent = Intent(this, ResultActivity::class.java)
        intent.putExtra(ResultActivity.EXTRA_NAME, data?.label)
        intent.putExtra(ResultActivity.EXTRA_SCORE,data?.score)
        intent.putExtra(ResultActivity.EXTRA_URI, currentImageUri.toString())
        startActivity(intent)
    }

    private fun runEditImage() {
        currentImageUri?.let {
            val tempFile = File.createTempFile("temp_image_edit", ".png")
            val destinationUri = Uri.fromFile(tempFile)
            val intent = UCrop.of(it, destinationUri)
                .withAspectRatio(16F, 9F)
                .getIntent(this)
            editImageLauncher.launch(intent)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun onBackButtonClicked() {
        val currentTime = System.currentTimeMillis()
        val closeTime = startBackButtonTime + BACK_DURATION
        if (currentTime < closeTime) {
            finish()
        } else {
            showToast("Press back again")
            startBackButtonTime = currentTime
        }
    }

    private fun showSampleDialog() {
        SampleImagesDialog { resId ->
            if (resId != null) {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.progressIndicator.isVisible = true
                    disableAllButton()
                    invalidateOptionsMenu()

                    val uri = withContext(Dispatchers.IO) {
                        val bitmap = BitmapFactory.decodeResource(resources, resId)
                        getImageUri(bitmap)
                    }

                    binding.progressIndicator.isVisible = false
                    currentImageUri = uri
                    showImage()
                    enableAllButton()
                    invalidateOptionsMenu()
                }
            }
        }.show(supportFragmentManager, null)
    }

    private fun disableAllButton() = with(binding) {
        galleryButton.isEnabled = false
        analyzeButton.isEnabled = false
    }

    private fun enableAllButton() = with(binding) {
        galleryButton.isEnabled = true
        analyzeButton.isEnabled = currentImageUri != null
    }

    companion object {
        private const val TAG = "MAIN-ACTIVITY"
        private const val BACK_DURATION = 2_000L
        private const val MENU_SAMPLE = 1
        private const val MENU_EDIT = 2
    }

}