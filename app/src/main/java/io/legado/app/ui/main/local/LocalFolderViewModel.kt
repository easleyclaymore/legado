package io.legado.app.ui.main.local

import android.app.Application
import android.os.Environment
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.utils.toastOnUi
import java.io.File

/**
 * 本地文件夹（主界面第三栏）
 */
class LocalFolderViewModel(application: Application) : BaseViewModel(application) {

    val rootDoc: File? = Environment.getExternalStorageDirectory()
    var subDocs = mutableListOf<File>()
    val filesLiveData = MutableLiveData<List<File>>()

    val lastDir: File? get() = subDocs.lastOrNull() ?: rootDoc

    fun upFiles(parentFile: File?) {
        execute {
            parentFile ?: return@execute emptyList()
            if (parentFile == rootDoc) {
                parentFile.listFiles()?.sortedWith(
                    compareBy({ it.isFile }, { it.name })
                )
            } else {
                val list = arrayListOf(parentFile)
                parentFile.listFiles()?.sortedWith(
                    compareBy({ it.isFile }, { it.name })
                )?.let {
                    list.addAll(it)
                }
                list
            }
        }.onStart {
            filesLiveData.postValue(emptyList())
        }.onSuccess {
            filesLiveData.postValue(it ?: emptyList())
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }
    }

}
