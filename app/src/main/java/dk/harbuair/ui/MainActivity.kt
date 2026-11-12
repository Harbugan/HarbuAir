package dk.harbuair.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dk.harbuair.audio.ToneChannels
import dk.harbuair.diag.DiagLog
import dk.harbuair.engine.NativeEngine
import kotlinx.coroutines.launch

/** Deliberately plain, programmatic UI (Phase 1: function over form). */
class MainActivity : AppCompatActivity() {
    private val vm: StreamViewModel by viewModels()
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var diag: TextView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private var pinDialog: AlertDialog? = null
    private val diagListener = { runOnUiThread { diag.text = DiagLog.snapshot() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        status = TextView(this).apply { textSize = 13f }
        list = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_SINGLE }
        startBtn = Button(this).apply { text = "Start tone"; setOnClickListener { vm.start() } }
        stopBtn = Button(this).apply { text = "Stop"; setOnClickListener { vm.stop() } }
        val btns = LinearLayout(this).apply { addView(startBtn, lp()); addView(stopBtn, lp()) }

        val toneSpin = Spinner(this).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, ToneChannels.values().map { it.label })
            onItemSelectedListener = sel { vm.setTone(ToneChannels.values()[it]) }
        }
        val authOpts = listOf<NativeEngine.Auth?>(null) + NativeEngine.Auth.values()
        val authSpin = Spinner(this).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                authOpts.map { it?.label ?: "Auth: auto (by device type)" })
            onItemSelectedListener = sel { vm.setAuth(authOpts[it]) }
        }
        val strict = CheckBox(this).apply { text = "Strict receiver auth (fail closed)"; setOnCheckedChangeListener { _, v -> vm.setStrict(v) } }
        val vol = SeekBar(this).apply {
            max = 100; progress = 20
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) vm.setVolume(p) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        val forget = Button(this).apply { text = "Forget pairing"; setOnClickListener { vm.forgetPairing() } }
        diag = TextView(this).apply { textSize = 10f; typeface = android.graphics.Typeface.MONOSPACE }
        val scroll = ScrollView(this).apply { addView(diag) }

        root.addView(TextView(this).apply { text = "Receivers (tap to select)" })
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(toneSpin); root.addView(authSpin); root.addView(strict)
        root.addView(TextView(this).apply { text = "Receiver volume (applied once streaming)" }); root.addView(vol)
        root.addView(btns); root.addView(forget); root.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1.3f))
        setContentView(root)

        list.setOnItemClickListener { _, _, pos, _ -> vm.state.value.receivers.getOrNull(pos)?.let(vm::select) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.state.collect { render(it) } }
        }
    }

    override fun onStart() { super.onStart(); vm.startDiscovery(); DiagLog.addListener(diagListener); diagListener() }

    override fun onStop() {
        // Phase 1 policy: no background streaming. Stop cleanly (TEARDOWN) when the UI is not visible.
        DiagLog.removeListener(diagListener)
        if (!isChangingConfigurations) { vm.stop(); vm.stopDiscovery() } else vm.stopDiscovery()
        super.onStop()
    }

    private fun render(s: UiState) {
        val rows = s.receivers.map { r ->
            val sel = if (r.key == s.selected?.key) "▶ " else ""
            "$sel${r.name}\n  ${r.host ?: "resolving…"}:${r.port ?: "?"}  ${r.type.label}  model=${r.model ?: "?"}\n" +
                "  features=${r.features ?: "?"} flags=${r.statusFlags ?: "?"} pw=${r.passwordRequired} gid=${r.groupId ?: "-"}"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        status.text = buildString {
            appendLine("State: ${s.conn}   Selected: ${s.selected?.name ?: "-"}")
            appendLine("Auth: ${s.effectiveAuth.label}   Pairing: ${s.pairing}")
            appendLine("Format: ${s.format}")
            if (s.error != null) appendLine("ERROR: ${s.error}")
        }
        val busy = s.conn == ConnState.CONNECTING || s.conn == ConnState.STREAMING || s.conn == ConnState.PIN_REQUIRED
        startBtn.isEnabled = !busy && s.selected?.host != null
        stopBtn.isEnabled = busy
        if (s.conn == ConnState.PIN_REQUIRED && pinDialog == null) showPin(s.selected?.name ?: "receiver")
        if (s.conn != ConnState.PIN_REQUIRED) { pinDialog?.dismiss(); pinDialog = null }
    }

    private fun showPin(name: String) {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "4-digit PIN" }
        pinDialog = AlertDialog.Builder(this).setTitle("PIN shown on $name").setView(input).setCancelable(false)
            .setPositiveButton("Pair") { _, _ -> pinDialog = null; vm.submitPin(input.text.toString()) }
            .setNegativeButton("Cancel") { _, _ -> pinDialog = null; vm.stop() }.show()
    }

    private fun lp() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun sel(f: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = f(pos)
        override fun onNothingSelected(p: AdapterView<*>?) {}
    }
}
