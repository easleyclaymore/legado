package io.legado.app.ui.main.local

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppConst
import io.legado.app.databinding.ItemFileBinding
import io.legado.app.databinding.ItemPathPickerBinding
import io.legado.app.databinding.FragmentLocalFolderBinding
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.association.FileAssociationActivity
import io.legado.app.ui.file.utils.FilePickerIcon
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.File

/**
 * 本地文件夹（主界面第三栏），浏览手机目录，点击书籍文件直接导入阅读
 */
class LocalFolderFragment() : VMBaseFragment<LocalFolderViewModel>(
    R.layout.fragment_local_folder
), MainFragmentInterface {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override val viewModel by viewModels<LocalFolderViewModel>()

    private val binding by viewBinding(FragmentLocalFolderBinding::bind)

    private val dirParent = ".."
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private val pathAdapter by lazy {
        PathAdapter()
    }
    private val fileAdapter by lazy {
        FileAdapter()
    }
    private val currentFiles = arrayListOf<File>()
    private var permissionRequested = false

    private val bookExtensions = setOf(
        "txt", "epub", "pdf", "mobi", "azw", "azw3", "umd", "cbz"
    )

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        initSearchView()
        initRecyclerView()
        checkStoragePermission {
            viewModel.upFiles(viewModel.rootDoc)
        }
    }

    private fun checkStoragePermission(success: (() -> Unit)? = null) {
        PermissionsCompat.Builder()
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                permissionRequested = true
                success?.invoke()
            }
            .onDenied {
                if (!permissionRequested) {
                    binding.tvEmptyMsg.text = getString(R.string.tip_perm_request_storage)
                    binding.tvEmptyMsg.visibility = View.VISIBLE
                }
            }
            .request()
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.queryHint = getString(R.string.screen) + " • " + getString(R.string.local_folder)
        searchView.isSubmitButtonEnabled = true
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                updateFiles()
                return false
            }
        })
    }

    private fun initRecyclerView() {
        binding.rvPath.layoutManager =
            LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.rvPath.adapter = pathAdapter
        binding.recyclerView.adapter = fileAdapter
    }

    private fun updateFiles() {
        if (searchView.query.isNotEmpty()) {
            currentFiles.filter {
                it.name == dirParent || it.name.contains(searchView.query)
            }.let {
                fileAdapter.setItems(it)
            }
        } else {
            fileAdapter.setItems(currentFiles)
        }
    }

    private fun gotoLastDir() {
        viewModel.subDocs.removeLastOrNull()
        pathAdapter.setItems(viewModel.subDocs)
        viewModel.upFiles(viewModel.lastDir)
    }

    override fun observeLiveBus() {
        viewModel.filesLiveData.observe(viewLifecycleOwner) {
            searchView.setQuery("", false)
            currentFiles.clear()
            currentFiles.addAll(it)
            updateFiles()
            if (it.size <= 1) {
                binding.tvEmptyMsg.visibility = View.VISIBLE
            } else {
                binding.tvEmptyMsg.visibility = View.GONE
            }
        }
    }

    @SuppressLint("SetTextI18n")
    inner class PathAdapter :
        RecyclerAdapter<File, ItemPathPickerBinding>(requireContext()) {

        private val arrowIcon = ConvertUtils.toDrawable(FilePickerIcon.getArrow())

        init {
            addHeaderView {
                ItemPathPickerBinding.inflate(inflater, it, false).apply {
                    textView.text = "root"
                    imageView.setImageDrawable(arrowIcon)
                    root.setOnClickListener {
                        viewModel.subDocs.clear()
                        setItems(viewModel.subDocs)
                        viewModel.upFiles(viewModel.rootDoc)
                    }
                }
            }
        }

        override fun getViewBinding(parent: ViewGroup): ItemPathPickerBinding {
            return ItemPathPickerBinding.inflate(inflater, parent, false).apply {
                imageView.setImageDrawable(arrowIcon)
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemPathPickerBinding) {
            binding.root.setOnClickListener {
                viewModel.subDocs = viewModel.subDocs.subList(0, holder.layoutPosition)
                setItems(viewModel.subDocs)
                viewModel.upFiles(viewModel.subDocs.lastOrNull())
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemPathPickerBinding,
            item: File,
            payloads: MutableList<Any>
        ) {
            binding.textView.text = item.name
        }

    }

    inner class FileAdapter : RecyclerAdapter<File, ItemFileBinding>(requireContext()) {
        private val upIcon = ConvertUtils.toDrawable(FilePickerIcon.getUpDir())!!
        private val folderIcon = ConvertUtils.toDrawable(FilePickerIcon.getFolder())!!
        private val fileIcon = ConvertUtils.toDrawable(FilePickerIcon.getFile())!!

        override fun getViewBinding(parent: ViewGroup): ItemFileBinding {
            return ItemFileBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemFileBinding) {
            binding.root.setOnClickListener {
                val item = getItemByLayoutPosition(holder.layoutPosition)
                item?.let {
                    if (item == viewModel.lastDir) {
                        gotoLastDir()
                    } else if (item.isDirectory) {
                        viewModel.subDocs.add(item)
                        pathAdapter.setItems(viewModel.subDocs)
                        viewModel.upFiles(item)
                    } else {
                        openBookFile(item)
                    }
                }
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemFileBinding,
            item: File,
            payloads: MutableList<Any>
        ) {
            if (item == viewModel.lastDir) {
                binding.imageView.setImageDrawable(upIcon)
                binding.textView.text = dirParent
            } else if (item.isDirectory) {
                binding.imageView.setImageDrawable(folderIcon)
                binding.textView.text = item.name
            } else {
                binding.imageView.setImageDrawable(fileIcon)
                binding.textView.text = item.name
            }
        }

        private fun openBookFile(file: File) {
            val isBook = file.extension.lowercase() in bookExtensions
            if (!isBook) {
                context?.toastOnUi(getString(R.string.wrong_format))
                return
            }
            val uri = FileProvider.getUriForFile(
                requireContext(),
                AppConst.authority,
                file
            )
            startActivity<FileAssociationActivity> {
                action = Intent.ACTION_VIEW
                data = uri
            }
        }

    }

}
