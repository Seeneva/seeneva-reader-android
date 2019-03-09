package com.almadevelop.comixreader

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val model by lazy { Model(applicationContext) }

//    fun o(u: Uri) {
//        contentResolver.takePersistableUriPermission(
//            u,
//            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
//        )
//
//        object : AsyncTask<Void, Void, ByteBuffer>() {
//            override fun doInBackground(vararg params: Void?): ByteBuffer? {
//                //val i = contentResolver.openInputStream(u).bufferedReader()
//
//                val fd = contentResolver.openFileDescriptor(u, "r")
//
//                // fd.fileDescriptor.sync()
//
////                return openZip(fd.fd, object: Callback{
////                    override fun onPagesBatchPrepared() {
////                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
////                    }
////                })
//                return null
//            }
//
//            override fun onPostExecute(result: ByteBuffer?) {
//                super.onPostExecute(result)
//
//                getSharedPreferences("test", Context.MODE_PRIVATE).edit { putString("test", u.toString()) }
//
//                Glide.with(this@MainActivity).load(ByteBufferUtil.toBytes(result!!)).into(imageView)
//            }
//        }.execute()
////        val b = openZip(contentResolver.openFileDescriptor(u, "r").fd)
////
////        Glide.with(this).load(ByteBufferUtil.toBytes(b)).into(imageView)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        model.hashCode()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.m, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.open -> {
                requestOpenComicsBook()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_OPEN_COMICS_BOOK -> {
                if (resultCode == Activity.RESULT_OK) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        data?.data?.also { uri ->
                            val fileName = contentResolver.query(
                                uri,
                                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                                null,
                                null,
                                null
                            )?.use {
                                if (it.moveToFirst()) {
                                    it.getString(0)
                                } else {
                                    null
                                }
                            }?.let { fileName ->
                                contentResolver.openFileDescriptor(uri, "r")
                                    ?.let {
                                        //[-1.579151, -1.4305071, -1.1332191, -1.579151, -1.4305071, -1.1332191]
                                        //openComicBook(it.fd, fileName)

                                        model.openComicArchive(it.fd)

                                        //Glide.with(this@MainActivity).load(ByteBufferUtil.toBytes(bs)).into(imageView)
                                    }
                            }

                        }
                    }
                }
            }
        }
    }

    private fun requestOpenComicsBook() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val mimeTypes = arrayOf(
                //"application/x-7z-compressed"
                "application/vnd.comicbook-rar"
            )

            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
                .setType("*/*")
            //.setType("application/octet-stream")
        } else {
            Intent(Intent.ACTION_GET_CONTENT)
            TODO()
        }.run { startActivityForResult(this, REQUEST_OPEN_COMICS_BOOK) }
    }


    //external fun parsePredictions(assetManager: AssetManager, preds: Array<Array<FloatArray>>): Array<Map<Any, Any>>

//    external fun parsePrediction(
//        assetManager: AssetManager,
//        pred: Array<Array<FloatArray>>,
//        batchSize: Int
//    ): Array<FloatArray>

    private companion object {
        private const val REQUEST_OPEN_COMICS_BOOK = 101;
    }
}
