package ir.jrweb.banksms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSms = findViewById<Button>(R.id.btnSmsPermission)
        val btnOverlay = findViewById<Button>(R.id.btnOverlayPermission)
        val btnTestDeposit = findViewById<Button>(R.id.btnTestDeposit)
        val btnTestWithdraw = findViewById<Button>(R.id.btnTestWithdraw)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        btnSms.setOnClickListener {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
                SMS_PERMISSION_CODE
            )
        }

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "مجوز نمایش روی صفحه فعال است", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestDeposit.setOnClickListener {
            showTestPopup("DEPOSIT", "2,500,000")
        }

        btnTestWithdraw.setOnClickListener {
            showTestPopup("WITHDRAW", "20,000")
        }

        updateStatus(tvStatus)
    }

    override fun onResume() {
        super.onResume()
        updateStatus(findViewById(R.id.tvStatus))
    }

    private fun updateStatus(tvStatus: TextView) {
        val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

        tvStatus.text = "وضعیت مجوز پیامک: ${if (smsGranted) "فعال ✅" else "غیرفعال ❌"}\n" +
                "وضعیت مجوز پاپ‌آپ: ${if (overlayGranted) "فعال ✅" else "غیرفعال ❌"}"
    }

    private fun showTestPopup(type: String, amount: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "ابتدا مجوز نمایش روی صفحه را فعال کنید", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, PopupService::class.java).apply {
            putExtra("TYPE", type)
            putExtra("AMOUNT", amount)
        }
        startService(intent)
    }
}