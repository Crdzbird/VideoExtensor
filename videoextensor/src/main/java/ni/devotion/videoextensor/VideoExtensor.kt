package ni.devotion.videoextensor

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.util.AttributeSet
import android.util.Log
import android.util.Patterns
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import java.io.IOException
import java.util.*

class VideoExtensor:FrameLayout, TextureView.SurfaceTextureListener {
    private var FILE_NAME:String = ""
    private var VIDEO_GRAVITY:Int = 0
    private var VOLUME: Float = 0f
    private lateinit var videoSurface:TextureView
    private var mVideoWidth:Float = 0.toFloat()
    private var mVideoHeight:Float = 0.toFloat()
    private val TAG = "VideoExtensor"
    private var mediaPlayer:MediaPlayer? = null
    private var IS_LOOP:Boolean = false
    enum class VGravity { start, end, centerCrop, none;
        val value:Int
            get() {
                return when (this) {
                    end -> 1
                    none -> 3
                    start -> 0
                    centerCrop -> 2
                    else -> 2
                }
            }
    }
    constructor(@NonNull context:Context) : super(context) {}
    constructor(@NonNull context:Context, @Nullable attrs:AttributeSet) : super(context, attrs) {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.VideoExtensor, 0, 0)
        a.let {
            VOLUME = it.getFloat(R.styleable.VideoExtensor_volume, 0f)
            FILE_NAME = it.getString(R.styleable.VideoExtensor_videoOrigin)!!
            VIDEO_GRAVITY = it.getInteger(R.styleable.VideoExtensor_gravity, 2)
            IS_LOOP = it.getBoolean(R.styleable.VideoExtensor_is_loop, true)
            it.recycle()
        }
        initViews()
        addView(videoSurface)
        setListeners()
        if (VIDEO_GRAVITY != 3) {
            calculateVideoSize()
            surfaceSetup()
        }
    }
    private fun initViews() {
        videoSurface = TextureView(context)
    }
    private fun setListeners() {
        videoSurface.surfaceTextureListener = this
    }
    private fun calculateVideoSize() {
        try {
            FILE_NAME.contains("http://") || FILE_NAME.contains("https://")
            val metaRetriever = MediaMetadataRetriever()
            if(Patterns.WEB_URL.matcher(FILE_NAME).matches()) {
                metaRetriever.setDataSource(FILE_NAME, HashMap())
            } else{
                val afd = context.assets.openFd(FILE_NAME)
                metaRetriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            val height = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val width = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            mVideoHeight = java.lang.Float.parseFloat(height)
            mVideoWidth = java.lang.Float.parseFloat(width)
            metaRetriever.release()
        }
        catch (e:IOException) {
            Log.d(TAG, e.message!!)
            e.printStackTrace()
        }
        catch (e:NumberFormatException) {
            Log.d(TAG, e.message!!)
            e.printStackTrace()
        }
    }
    private fun updateTextureViewSize(viewWidth:Int, viewHeight:Int) {
        var scaleX = 1.0f
        var scaleY = 1.0f
        if (mVideoWidth > viewWidth && mVideoHeight > viewHeight) {
            scaleX = mVideoWidth / viewWidth
            scaleY = mVideoHeight / viewHeight
        }
        else if (mVideoWidth < viewWidth && mVideoHeight < viewHeight) {
            scaleY = viewWidth / mVideoWidth
            scaleX = viewHeight / mVideoHeight
        }
        else if (viewWidth > mVideoWidth) {
            scaleY = (viewWidth / mVideoWidth) / (viewHeight / mVideoHeight)
        }
        else if (viewHeight > mVideoHeight) {
            scaleX = (viewHeight / mVideoHeight) / (viewWidth / mVideoWidth)
        }
        val pivotPointX = if ((VIDEO_GRAVITY == 0)) 0 else if ((VIDEO_GRAVITY == 1)) viewWidth else viewWidth / 2
        val pivotPointY = viewHeight / 2
        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, pivotPointX.toFloat(), pivotPointY.toFloat())
        videoSurface.setTransform(matrix)
        videoSurface.layoutParams = LayoutParams(viewWidth, viewHeight)
    }

    private fun surfaceSetup() {
        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth = resources.displayMetrics.widthPixels
        updateTextureViewSize(screenWidth, screenHeight)
    }

    private fun surfaceAvaibleWorkers(surfaceTexture:SurfaceTexture) {
        val surface = Surface(surfaceTexture)
        try {
            mediaPlayer = MediaPlayer()
            if(Patterns.WEB_URL.matcher(FILE_NAME).matches()){
                mediaPlayer?.setDataSource(FILE_NAME)
            }else {
                val afd = context.assets.openFd(FILE_NAME)
                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mediaPlayer?.setVolume(VOLUME, VOLUME)
            mediaPlayer?.setSurface(surface)
            mediaPlayer?.isLooping = IS_LOOP
            mediaPlayer?.prepareAsync()
            mediaPlayer?.setOnPreparedListener(MediaPlayer::start)
        }
        catch (ignored: Exception) { }
    }
    
    private fun changeVideo() {
        try {
            onDestroyVideoExtensor()
            mediaPlayer = MediaPlayer()
            if(Patterns.WEB_URL.matcher(FILE_NAME).matches()) {
                mediaPlayer?.setDataSource(FILE_NAME)
            }else {
                val afd = context.assets.openFd(FILE_NAME)
                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mediaPlayer?.setVolume(0f, 0f)
            mediaPlayer?.isLooping = IS_LOOP
            mediaPlayer?.setSurface(Surface(videoSurface.surfaceTexture))
            mediaPlayer?.prepareAsync()
            mediaPlayer?.setOnPreparedListener(MediaPlayer::start)
        }
        catch (ignored: Exception) { }
    }
    
    override fun onSurfaceTextureAvailable(surface:SurfaceTexture, width:Int, height:Int) = surfaceAvaibleWorkers(surface)
    
    override fun onSurfaceTextureSizeChanged(surface:SurfaceTexture, width:Int, height:Int) {}
    
    override fun onSurfaceTextureDestroyed(surface:SurfaceTexture) = false
    
    override fun onSurfaceTextureUpdated(surface:SurfaceTexture) {}

    fun onDestroyVideoExtensor() {
        mediaPlayer?.let { 
            it.stop()
            it.release()
        }
        mediaPlayer = null
    }
    
    fun onResumeVideoExtensor() {
        mediaPlayer?.let { 
            if(!it.isPlaying) it.start()
        }
    }
    
    fun onPauseVideoExtensor() {
        mediaPlayer?.let { 
            if (it.isPlaying) it.pause()
        }
    }
    
    fun setInits(){
        initViews()
        addView(videoSurface)
        setListeners()
    }
    
    fun setPathOrUrl(FILE_NAME:String) {
        this.FILE_NAME = FILE_NAME
        videoSurface ?: setInits()
        if (VIDEO_GRAVITY != 3) {
            calculateVideoSize()
            surfaceSetup()
        }
        videoSurface?.let { 
            changeVideo()
        }
    }

    fun setVolume(VOLUME: Float){
        this.VOLUME = VOLUME
    }
    
    fun setIsLoop(IS_LOOP:Boolean) {
        this.IS_LOOP = IS_LOOP
    }
    fun setGravity(gravity:VGravity) {
        VIDEO_GRAVITY = gravity.value
    }
}