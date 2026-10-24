package com.local.unifiedinbox.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.local.unifiedinbox.AppGraph
import com.local.unifiedinbox.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HitLogActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: HitLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        AppGraph.initialize(this)
        setContentView(R.layout.activity_hit_log)

        recycler = findViewById(R.id.recyclerHitLog)
        emptyView = findViewById(R.id.tvHitLogEmpty)
        adapter = HitLogAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadHitLog()
    }

    private fun loadHitLog() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                AppGraph.repository.hitLog(50)
            }
            adapter.submit(entries)
            emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
