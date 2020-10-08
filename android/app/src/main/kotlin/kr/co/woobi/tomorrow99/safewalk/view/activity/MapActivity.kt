package kr.co.woobi.tomorrow99.safewalk.view.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.PointF
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.util.FusedLocationSource
import com.naver.maps.map.util.MarkerIcons
import com.sungbin.sungbintool.extensions.afterTextChanged
import com.sungbin.sungbintool.extensions.get
import com.sungbin.sungbintool.extensions.toEditable
import com.sungbin.sungbintool.util.Logger
import com.sungbin.sungbintool.util.ToastLength
import com.sungbin.sungbintool.util.ToastType
import com.sungbin.sungbintool.util.ToastUtil
import com.sungbin.sungbintool.util.Util.doDelay
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_map.*
import kr.co.woobi.tomorrow99.safewalk.R
import kr.co.woobi.tomorrow99.safewalk.`interface`.AddressInterface
import kr.co.woobi.tomorrow99.safewalk.`interface`.ImageInterface
import kr.co.woobi.tomorrow99.safewalk.`interface`.PingInfoInterface
import kr.co.woobi.tomorrow99.safewalk.adapter.TagAdapter
import kr.co.woobi.tomorrow99.safewalk.model.*
import kr.co.woobi.tomorrow99.safewalk.tool.calDistance
import kr.co.woobi.tomorrow99.safewalk.tool.util.ColorUtil
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.jetbrains.anko.startActivity
import retrofit2.Retrofit
import java.io.File
import java.net.URI
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    @Named("locationApi")
    @Inject
    lateinit var locationApi: Retrofit

    private lateinit var naverMap: NaverMap
    private lateinit var imagePing:ImageView

    private var userInfo = User(
        null,
        null,
        null,
        null,
        null
    )

    private val locationPermissionCode = 1000
    private val GET_GALLERY_IMAGE = 2000
    private val LOGIN_REQUEST_CODE = 3000

    @Named("server")
    @Inject
    lateinit var server: Retrofit

    var pingData = HashMap<String, DangerInformation>()
    var centerPing = Marker()

    private val locationSource by lazy {
        FusedLocationSource(this, locationPermissionCode)
    }

    private val tags = ArrayList<Tag>()
    private var UriImg:String? = null

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_map)

        (supportFragmentManager.findFragmentById(R.id.fcv_map) as MapFragment).getMapAsync(this)

        val headerLayout = LayoutInflater.from(applicationContext)
            .inflate(R.layout.layout_navigation_header, null, false)
        nv_navigation.addHeaderView(headerLayout)

        iv_navigation.setOnClickListener {
            dl_drawer.openDrawer(GravityCompat.START)
        }

        nv_navigation.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.menu_setting -> {
                    startActivity<SettingActivity>()
                }
            }
            true
        }

        TedPermission.with(this)
            .setPermissionListener(object : PermissionListener {
                override fun onPermissionGranted() {
                }

                override fun onPermissionDenied(deniedPermissions: List<String>?) {
                    ToastUtil.show(
                        applicationContext,
                        getString(R.string.map_permission_denied),
                        ToastLength.SHORT,
                        ToastType.INFO
                    )
                }

            })
            .setDeniedMessage(R.string.map_permission_denied)
            .setPermissions(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            .check()

        fab_location.setOnClickListener {
            naverMap.locationTrackingMode = LocationTrackingMode.Follow
        }

        checkGpsIsOn()

        fab_danger.setOnClickListener{
            btn_search_route.visibility = View.INVISIBLE
            if (btn_declaration.visibility == View.VISIBLE) {
                btn_declaration.visibility = View.INVISIBLE
                centerPing.map = null
            }
            else {
                btn_declaration.visibility = View.VISIBLE
                var center = naverMap.cameraPosition
                centerPing.position = LatLng(center.target.latitude, center.target.longitude)
                centerPing.icon = MarkerIcons.BLACK
                centerPing.map = naverMap
            }
        }

        btn_declaration.setOnClickListener {
            if (userInfo.session  == null){
                val intent = Intent(this, SigninActivity::class.java)
                startActivityForResult(intent, LOGIN_REQUEST_CODE)
            }
            else {
                tags.clear()
                val adapter = TagAdapter(tags, this)
                adapter.setOnClickListener {
                    tags.remove(it)
                    adapter.notifyDataSetChanged()
                }
                val layout = LayoutInflater.from(applicationContext)
                    .inflate(R.layout.layout_ping_set_dialog, null, false)

                val selectedItems = ArrayList<Int>()
                (layout[R.id.tv_tag_add] as TextView).setOnClickListener {
                    tags.clear()
                    selectedItems.clear()
                    val builder: AlertDialog.Builder = AlertDialog.Builder(this)

                    builder.setTitle("태그를 선택해 주세요.")

                    builder.setMultiChoiceItems(
                        TAG_LIST,
                        null
                    ) { _, pos, isChecked ->
                        if (isChecked) // Checked 상태일 때 추가
                        {
                            selectedItems.add(pos)
                        } else  // Check 해제 되었을 때 제거
                        {
                            selectedItems.remove(pos)
                        }
                    }

                    builder.setPositiveButton("태그 수정"
                    ) { _, pos ->
                        for (i in selectedItems) {
                            tags.add(Tag(TAG_LIST[i], ColorUtil.randomColor))
                            adapter.notifyDataSetChanged()
                        }
                    }

                    val alertDialog: AlertDialog = builder.create()
                    alertDialog.show()
                }

                (layout[R.id.rv_tag] as RecyclerView).adapter = adapter

                imagePing = (layout[R.id.iv_lens] as ImageView)
                imagePing.setOnClickListener {
                    val intent = Intent(Intent.ACTION_PICK)
                    intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                    startActivityForResult(intent, GET_GALLERY_IMAGE)
                }

                //위치
                locationApi.create(AddressInterface::class.java).run {
                    getAddress(
                        "${centerPing.position.longitude},${centerPing.position.latitude}",
                        "epsg:4326",
                        "roadaddr",
                        "json"
                    )
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ response ->
                            response.results?.get(0)?.let {
                                var locationData = ""

                                for (data in it.region) {
                                    if (data.key == "area0") continue
                                    locationData = "$locationData ${data.value.name}"
                                }

                                (layout[R.id.tv_location] as TextView).text = locationData
                            }
                        }, { throwable ->
                            Logger.w(throwable)
                            (layout[R.id.tv_location] as TextView).text =
                                "${
                                centerPing.position.latitude.toString().substring(0..5)
                                },  ${
                                centerPing.position.longitude.toString().substring(0..5)
                                }"
                        }, {
                        })
                }

                //위험등급
                val SKULL = listOf(
                    layout[R.id.iv_skull0] as ImageView,
                    layout[R.id.iv_skull1] as ImageView,
                    layout[R.id.iv_skull2] as ImageView,
                    layout[R.id.iv_skull3] as ImageView,
                    layout[R.id.iv_skull4] as ImageView
                )

                for (i in 0..4){
                    SKULL[i].setOnClickListener{
                        for (idx in 0..i) run {
                            SKULL[idx].setImageResource(R.drawable.skull3)
                        }

                        for (idx in (i+1)..4) run {
                            if (i != 4) SKULL[idx].setImageResource(R.drawable.skull1)
                        }
                        (layout[R.id.tv_danger_level] as TextView).text = (i+1).toString()+".0"
                    }
                }

                (layout[R.id.btn_done] as MaterialButton).setOnClickListener {
                    server.create(PingInfoInterface::class.java).run {
                        addPing(
                            SetPingIn(
                                userInfo.session,
                                centerPing.position.latitude.toString(),
                                centerPing.position.longitude.toString(),
                                (layout[R.id.tv_danger_level] as TextView).text.toString()
                                    .toDouble()/5,
                                selectedItems
                            )
                        )
                            .subscribeOn(Schedulers.computation())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ response ->
                                if (response.result == "success") {
                                    if(UriImg != null){
                                        val file = File(UriImg!!)
                                        var fileName = response.id.toString()+".png"

                                        var requestBody : RequestBody = RequestBody.create("image/*".toMediaTypeOrNull(),file)
                                        var body : MultipartBody.Part = MultipartBody.Part.createFormData("uploaded_file",fileName,requestBody)

                                        server.create(ImageInterface::class.java).run {
                                            postImage(
                                                body
                                            )
                                                .subscribeOn(Schedulers.computation())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe({ response ->
                                                    if (response.result == "success") {
                                                        Toast.makeText(applicationContext, "업로드 완료", Toast.LENGTH_LONG).show();
                                                    }
                                                    else {
                                                        Toast.makeText(
                                                            this@MapActivity,
                                                            "${response}.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }, { throwable ->
                                                    Logger.w(throwable)
                                                    Toast.makeText(
                                                        this@MapActivity,
                                                        "${throwable.message}.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }, {
                                                })
                                        }
                                    }

                                }
                                else {
                                    Toast.makeText(
                                        this@MapActivity,
                                        "${response.comment!!}.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }, { throwable ->
                                Logger.w(throwable)
                                Toast.makeText(
                                    this@MapActivity,
                                    "${throwable.message}.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }, {
                            })
                    }

                }

                val dialog = MaterialAlertDialogBuilder(this@MapActivity)
                dialog.setView(layout)
                dialog.show()
            }
        }

        tv_location.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, start: Int, count: Int, after: Int) {
                if (after == 0) {
                    btn_search_route.visibility = View.INVISIBLE
                    return;
                }

                if (btn_declaration.visibility == View.VISIBLE) {
                    centerPing.map = null
                    btn_declaration.visibility = View.INVISIBLE
                }
                btn_search_route.visibility = View.VISIBLE
            }

            override fun onTextChanged(p0: CharSequence?, start: Int, before: Int, count: Int) {
                //todo
            }

            override fun afterTextChanged(p0: Editable?) {
                //todo
            }
        })
    }

    private fun checkGpsIsOn() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            ToastUtil.show(
                applicationContext,
                getString(R.string.map_need_gps),
                ToastLength.SHORT,
                ToastType.WARNING
            )

            doDelay({
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                })
            }, 1500)
        }
    }

    @SuppressLint("SetTextI18n", "ResourceAsColor")
    override fun onMapReady(map: NaverMap) {
        naverMap = map
        map.uiSettings.apply {
            isLogoClickEnabled = false
            isCompassEnabled = false
            isZoomControlEnabled = false
            isZoomGesturesEnabled = true
            logoGravity = Gravity.TOP or Gravity.END
            isScaleBarEnabled = false
            isLocationButtonEnabled = false
            setLogoMargin(16, 16, 16, 16)
        }

        map.locationSource = locationSource
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PermissionChecker.PERMISSION_GRANTED &&
            PermissionChecker.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ==
            PermissionChecker.PERMISSION_GRANTED
        ) {
            naverMap.locationTrackingMode = LocationTrackingMode.Follow
        }

        naverMap.addOnCameraChangeListener { reason, animated ->
            tv_location.text = null
            btn_search_route.visibility = View.INVISIBLE

            var center = naverMap.cameraPosition
            if (btn_declaration.visibility == View.VISIBLE) {
                centerPing.position = LatLng(center.target.latitude, center.target.longitude)
                centerPing.icon = MarkerIcons.BLACK
                centerPing.map = naverMap
            }
            else {
                centerPing.map = null
            }

            if(center.zoom > 13){
                server.create(PingInfoInterface::class.java).run {
                    getPingData(
                        GetPingIn(
                            (calDistance(
                                center.target.latitude,
                                center.target.longitude,
                                map.projection.fromScreenLocation(PointF(0f, 0f)).latitude,
                                map.projection.fromScreenLocation(PointF(0f, 0f)).longitude
                            ) * 10).toString(),
                            center.target.latitude.toString(),
                            center.target.longitude.toString()
                        )
                    )
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ response ->
                            val setCorlor = 0.876

                            if (response.result == "success") {
                                for (ping in response.data!!) {
                                    val marker = Marker()
                                    marker.position = LatLng(
                                        ping.location["latitude"] ?: 132.0,
                                        ping.location["longitude"] ?: 37.0
                                    )

                                    marker.tag = ping.id
                                    var red = 219.0
                                    var green = 219.0

                                    if (ping.level * 5 < 2.5) {
                                        red = ping.level * 500 * setCorlor
                                    }
                                    if (ping.level * 5 > 2.5) {
                                        green = 219 - ((ping.level * 500 * setCorlor) - 219)
                                    }

                                    marker.icon = MarkerIcons.BLACK
                                    marker.iconTintColor = Color.rgb(red.toInt(), green.toInt(), 0)
                                    marker.map = naverMap

                                    marker.setOnClickListener {
                                        val TAG_INFO_DATA = pingData[it.tag.toString()]
                                        Logger.w(TAG_INFO_DATA)

                                        ////////////////////////////////////////////////////
                                        tags.clear()
                                        val adapter = TagAdapter(tags, this@MapActivity)
                                        adapter.setOnClickListener {
                                            tags.remove(it)
                                            adapter.notifyDataSetChanged()
                                            Logger.w(it.label)
                                        }
                                        val layout = LayoutInflater.from(applicationContext)
                                            .inflate(R.layout.layout_ping_info_dialog, null, false)

                                        for (tag in TAG_INFO_DATA!!.tag) {
                                            tags.add(Tag(TAG_LIST[tag%TAG_LIST.size], ColorUtil.randomColor))
                                            adapter.notifyDataSetChanged()
                                        }

                                        (layout[R.id.rv_tag] as RecyclerView).adapter = adapter

                                        // 이미지 출력
                                        val HOST = "http://210.107.245.192:400/"
                                        val IMG_URL =
                                            HOST + "getImagePing.php?id=" + TAG_INFO_DATA.id

                                        var infoImage = (layout[R.id.iv_lens] as ImageView)
                                        Logger.w(IMG_URL)

                                        Glide.with(this@MapActivity).load(IMG_URL)
                                            .apply(
                                                RequestOptions.overrideOf(
                                                    infoImage.width,
                                                    infoImage.height
                                                )
                                            )
                                            .apply(RequestOptions.centerCropTransform())
                                            .into(layout[R.id.iv_lens] as ImageView)

                                        // 주소 출력
                                        locationApi.create(AddressInterface::class.java).run {
                                            getAddress(
                                                "${TAG_INFO_DATA.location["longitude"]},${TAG_INFO_DATA.location["latitude"]}",
                                                "epsg:4326",
                                                "roadaddr",
                                                "json"
                                            )
                                                .subscribeOn(Schedulers.computation())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe({ response ->
                                                    response.results?.get(0)?.let {
                                                        var locationData = ""

                                                        for (data in it.region) {
                                                            if (data.key == "area0") continue
                                                            locationData =
                                                                "$locationData ${data.value.name}"
                                                        }

                                                        (layout[R.id.tv_location] as TextView).text =
                                                            locationData.toEditable()
                                                    }
                                                }, { throwable ->
                                                    Logger.w(throwable)
                                                    (layout[R.id.tv_location] as TextView).text =
                                                        "${
                                                        center.target.latitude.toString()
                                                            .substring(
                                                                0..5
                                                            )
                                                        },  ${
                                                        center.target.longitude.toString()
                                                            .substring(
                                                                0..5
                                                            )
                                                        }".toEditable()
                                                }, {
                                                })
                                        }

                                        // 위험 등급
                                        val SKULL = listOf(
                                            layout[R.id.iv_skull0] as ImageView,
                                            layout[R.id.iv_skull1] as ImageView,
                                            layout[R.id.iv_skull2] as ImageView,
                                            layout[R.id.iv_skull3] as ImageView,
                                            layout[R.id.iv_skull4] as ImageView
                                        )

                                        val LEVEL = (TAG_INFO_DATA.level) * 5
                                        val IMG_IDX = LEVEL.toInt()
                                        for (idx in 0..IMG_IDX) {
                                            if (idx == 5 || IMG_IDX == 0) break
                                            SKULL[idx].setImageResource(R.drawable.skull3)
                                        }
                                        if ((LEVEL - IMG_IDX) >= 0.5) SKULL[IMG_IDX].setImageResource(
                                            R.drawable.skull2
                                        )
                                        else if (IMG_IDX != 5) SKULL[IMG_IDX].setImageResource(R.drawable.skull1)

                                        (layout[R.id.tv_danger_level_voted] as TextView).text =
                                            LEVEL.toString()

                                        // 유용성
                                        val TRUE_CNT = TAG_INFO_DATA.useful["true"]
                                        val FALSE_CNT = TAG_INFO_DATA.useful["false"]

                                        if (TRUE_CNT!! == FALSE_CNT!!) {
                                            (layout[R.id.ll_border_false] as LinearLayout).background.setTint(
                                                R.color.colorLightRed
                                            )
                                            (layout[R.id.ll_border_true] as LinearLayout).background.setTint(
                                                R.color.colorSky
                                            )
                                        } else if (TRUE_CNT > FALSE_CNT) {
                                            (layout[R.id.ll_border_usefull] as LinearLayout).background.setTint(
                                                R.color.colorSky
                                            )
                                            (layout[R.id.ll_border_false] as LinearLayout).background.setTint(
                                                R.color.colorLightRed
                                            )
                                            (layout[R.id.ll_border_true] as LinearLayout).background =
                                                null
                                        } else {
                                            (layout[R.id.ll_border_usefull] as LinearLayout).background.setTint(
                                                R.color.colorLightRed
                                            )
                                            (layout[R.id.ll_border_true] as LinearLayout).background.setTint(
                                                R.color.colorSky
                                            )
                                            (layout[R.id.ll_border_false] as LinearLayout).background =
                                                null
                                        }
                                        (layout[R.id.tv_good_count] as TextView).text =
                                            TRUE_CNT.toString()
                                        (layout[R.id.tv_bad_count] as TextView).text =
                                            FALSE_CNT.toString()

                                        val dialog = MaterialAlertDialogBuilder(this@MapActivity)
                                        dialog.setView(layout)
                                        dialog.show()

                                        false
                                        ////////////////////////////////////////////////////
                                    }

                                    pingData.put(ping.id.toString(), ping)

                                    //markerList.put(data.id.toString(),marker)
                                }
                            }
                        }, { throwable ->
                            Logger.w(throwable)
                            Toast.makeText(
                                this@MapActivity,
                                "${throwable.message}.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }, {
                        })
                }
            }
        }

        map.addOnLocationChangeListener {
            val center = naverMap.cameraPosition

            if (center.zoom > 13) {
                locationApi.create(AddressInterface::class.java).run {
                    getAddress(
                        "${center.target.longitude},${center.target.latitude}",
                        "epsg:4326",
                        "roadaddr",
                        "json"
                    )
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ response ->
                            response.results?.get(0)?.let {
                                var locationData = ""

                                for (data in it.region) {
                                    if (data.key == "area0") continue
                                    locationData = "$locationData ${data.value.name}"
                                }

                                tv_location.hint = locationData.toEditable()
                            }
                        }, { throwable ->
                            Logger.w(throwable)
                            tv_location.hint =
                                "${
                                center.target.latitude.toString().substring(0..5)
                                },  ${
                                center.target.longitude.toString().substring(0..5)
                                }".toEditable()
                        }, {
                        })
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(resultCode == RESULT_OK) {
            when (requestCode) {
                GET_GALLERY_IMAGE -> {
                    if (data != null && data.data != null) {
                        val selectedImageUri: Uri? = data.data
                        if(selectedImageUri != null)
                            UriImg = absolutelyPath(selectedImageUri)
                        imagePing.setImageURI(selectedImageUri)

                        Logger.w(UriImg.toString())
                    }
                }

                LOGIN_REQUEST_CODE -> {
                    userInfo.session = data?.getStringExtra("session")
                    userInfo.nickname = data?.getStringExtra("nickname")
                    userInfo.name = data?.getStringExtra("name")
                    userInfo.email = data?.getStringExtra("email")
                    userInfo.callNum = data?.getStringExtra("callnum")
                }
            }
        }


    }

    // 절대경로 변환
    fun absolutelyPath(path: Uri): String {
        var proj: Array<String> = arrayOf(MediaStore.Images.Media.DATA)
        var c: Cursor = contentResolver.query(path, proj, null, null, null)!!
        var index = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        c.moveToFirst()

        var result = c.getString(index)

        return result
    }
}
