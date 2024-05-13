import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch
import java.io.File
import java.util.regex.Pattern

@Composable
@Preview
fun App() {

    MaterialTheme {
        var processing by remember {
            mutableStateOf(false)
        }
        val scope = rememberCoroutineScope()
        val startProcess = { block: () -> Unit ->
            scope.launch {
                processing = true
                try {
                    block()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                processing = false
            }
            Unit
        }
        Frame(startProcess)
        if (processing) {
            AlertDialog({

            }, buttons = {

            }, title = {
                Text("Info")
            }, text = {
                Text("Processing")
            })
        }
    }
}

@Composable
private fun Frame(startProcess: (() -> Unit) -> Unit) {
    Row {
        var current by remember {
            mutableStateOf<String?>(null)
        }
        Column {
            var devices by remember {
                mutableStateOf<List<String>>(emptyList())
            }
            DisposableEffect(devices, current) {
                if (!devices.contains(current)) current = null
                onDispose {  }
            }
            Button({
                startProcess {
                    val p = ProcessBuilder("adb", "devices").start()
                    val readText = p.inputStream.bufferedReader().readText()
                    val code = p.waitFor()
                    if (code == 0) {
                        devices = readText.split(Pattern.compile("[\r\n]+")).let {
                            it.subList(1, it.size)
                        }.filter {
                            it.isNotEmpty()
                        }.map {
                            it.split(Pattern.compile("\\s+"))
                        }.filter {
                            it[1] != "offline"
                        }.map {
                            it.first()
                        }
                    } else {
                        println(p.errorStream.bufferedReader().readText())
                    }
                    p.destroy()
                }

            }) {
                Text("Refresh Devices")
            }

            LazyColumn {
                items(devices) {
                    Text(it, modifier = Modifier.padding(12.dp).clickable {
                        current = it
                    })
                }
            }
        }
        Column {
            if (current != null) {
                var processId by remember {
                    mutableStateOf(-1)
                }
                Row {
                    Button({
                        startProcess {
                            ProcessBuilder(
                                "scrcpy",
                                "--video-codec=h265",
                                "-m1920",
                                "--max-fps=60",
                                "--no-audio",
                                "--serial=$current",
                                "-K",
                                "--show-touches"
                            ).start()
                        }

                    }) {
                        Text("Connect")
                    }
                    Button({
                        startProcess {
                            processId = ProcessBuilder(
                                "powershell.exe",
                                "Get-WmiObject",
                                "Win32_Process",
                                "|",
                                "Where-Object",
                                "{ \$_.ProcessName -like '*scrcpy*' }",
                                "|",
                                "Select-Object ProcessId, Name, CommandLine"
                            ).start().inputStream.bufferedReader().readText().lines().let {
                                if (it.size > 2) {
                                    it.subList(2, it.size)
                                } else {
                                    emptyList()
                                }
                            }.firstOrNull {
                                it.contains("--serial=$current")
                            }?.trim()?.split(" ")?.first()?.toInt() ?: -1
                        }

                    }) {
                        Text("Refresh Process Status")
                    }
                    if (processId > 0) {
                        Button({
                            startProcess {
                                ProcessBuilder("powershell.exe", "kill", "$processId").start()
                            }
                        }) {
                            Text("Disconnect")
                        }

                        Button({
                            val content = File("events").readText()
                            startProcess {
                                content.split("\n").map {
                                    it.trim()
                                }.forEach {
                                    val split = it.split(" ").map {
                                        if (it.all { it.isLetterOrDigit() }) {
                                            it.toLong(16).toString()
                                        } else {
                                            it
                                        }
                                    }
                                    println(split)
                                    ProcessBuilder(
                                        "adb",
                                        "shell",
                                        "sendevent",
                                        *split.toTypedArray()
                                    ).start().waitFor()
                                }
                            }
                        }) {
                            Text("Script")
                        }
                    }
                }
                Text("Status: ${if (processId > 0) "on-$processId" else "off"}")
            }


        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
