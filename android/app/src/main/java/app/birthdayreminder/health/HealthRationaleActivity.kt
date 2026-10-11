package app.birthdayreminder.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Health Connect 要求的“为什么要读健康数据”说明页（在系统的权限界面里点“隐私政策”会打开这里） */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("日迹怎么使用健康数据", style = MaterialTheme.typography.titleLarge)
                        Text("日迹只读取你授权的心率、静息心率、睡眠、体重、身高、体脂、血压、血氧，用来填到“健身数据 → 身体数据”里，方便你查看和统计。")
                        Text("只读不写：日迹不会修改、删除 Health Connect 里的任何数据。")
                        Text("不上传：数据只保存在你的手机里（以及你自己导出的备份文件），日迹没有服务器。")
                        Text("随时可以在系统设置 → Health Connect 里取消授权。")
                        Button(onClick = { finish() }) { Text("知道了") }
                    }
                }
            }
        }
    }
}
