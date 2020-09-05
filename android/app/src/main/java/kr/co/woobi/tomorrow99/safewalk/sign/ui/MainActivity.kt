package kr.co.woobi.tomorrow99.safewalk.sign.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kr.co.woobi.tomorrow99.safewalk.R
import kr.co.woobi.tomorrow99.safewalk.library.ImageHelper
import kr.co.woobi.tomorrow99.safewalk.map.mainmapPage
import kr.co.woobi.tomorrow99.safewalk.sign.LoginOut
import kr.co.woobi.tomorrow99.safewalk.sign.LoginService
import kr.co.woobi.tomorrow99.safewalk.sign.isEmail
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        btn_login.setOnClickListener {
            val ID = te_id.text.toString()
            val PW =
                sha256(te_password.text.toString())

            val SERVE_HOST:String = "http://210.107.245.192:400/"
            var retrofit = Retrofit.Builder()
                .baseUrl(SERVE_HOST)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            var loginService = retrofit.create(LoginService::class.java)

            var body = HashMap<String, String>()

            body.put("id", ID)
            body.put("pwd", PW)

            loginService.requestLogin(body).enqueue(object : Callback<LoginOut>{
                override fun onFailure(call: Call<LoginOut>, t: Throwable) {
                    tv_emergencyError.setText(R.string.error_network)
                }

                override fun onResponse(call: Call<LoginOut>, response: Response<LoginOut>) {
                    try {
                        val RESPONSE_DATA = response.body() //responseData?.session 사용시 null 일 수도 있음

                        val dialog = AlertDialog.Builder(this@MainActivity)
                        dialog.setTitle("알람")
                        if(RESPONSE_DATA?.result == "success"){
                            //메인 화면 이동
                            val MAP_PAGE = Intent(this@MainActivity, mainmapPage::class.java)

                            MAP_PAGE.putExtra("session", RESPONSE_DATA.session)
                            MAP_PAGE.putExtra("nickname", RESPONSE_DATA.nickname)
                            MAP_PAGE.putExtra("name", RESPONSE_DATA.name)
                            MAP_PAGE.putExtra("email", RESPONSE_DATA.email)
                            MAP_PAGE.putExtra("callnum", RESPONSE_DATA.callNum)

                            MAP_PAGE.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            MAP_PAGE.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            setResult(Activity.RESULT_OK, MAP_PAGE)
                            finish()
                        }
                        else {
                            dialog.setMessage("result=${RESPONSE_DATA?.result}&comment=${RESPONSE_DATA?.comment}")
                            dialog.show()
                        }
                    }
                    catch (e:Exception){
                        Log.d("에러로그","$e")
                    }
                }
            })
        }

        val LOGIN_FIELD_CHANGE_LISTENER = object:TextWatcher{
            override fun afterTextChanged(s: Editable?) {
                if (te_id.text.toString() == "") tv_emergencyError.setText(R.string.error_emptyID)
                else if(!isEmail(te_id.text.toString())) tv_emergencyError.setText(
                    R.string.error_notEmail
                )
                else if (te_password.text.toString() == "") tv_emergencyError.setText(
                    R.string.error_emptyPW
                )
                else tv_emergencyError.text = ""
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }
        }

        te_password.addTextChangedListener(LOGIN_FIELD_CHANGE_LISTENER)
        te_id.addTextChangedListener(LOGIN_FIELD_CHANGE_LISTENER)

        tv_signUpLink.setOnClickListener{
            val INTENT = Intent(this, signup::class.java)
            startActivity(INTENT)
        }
    }
}

/****************************************
 * Name:            sha256
 * description:     sha256 해싱
 *
 * Author: Jeong MinGye
 * Create: 20.07.26
 * Update:
 *
 * //출처: https://lonepine.tistory.com/entry/Kotlin-Sha256 [Lonepine's blog]
 **************************************/
fun sha256(param: String): String {
    val HEX_CHARS = "0123456789ABCDEF"
    val bytes = MessageDigest
        .getInstance("SHA-256")
        .digest(param.toByteArray())
    val result = StringBuilder(bytes.size * 2)
    bytes.forEach {
        val i = it.toInt()
        result.append(HEX_CHARS[i shr 4 and 0x0f])
        result.append(HEX_CHARS[i and 0x0f])
    }
    return result.toString()
}


