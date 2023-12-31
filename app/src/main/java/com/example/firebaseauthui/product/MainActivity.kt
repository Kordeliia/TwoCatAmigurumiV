package com.example.firebaseauthui.product

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.firebaseauthui.add.AddDialogFragment
import com.example.firebaseauthui.Constants
import com.example.firebaseauthui.entities.Product
import com.example.firebaseauthui.R
import com.example.firebaseauthui.databinding.ActivityMainBinding
import com.example.firebaseauthui.order.OrderActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase


class MainActivity : AppCompatActivity(), OnProductListener, MainAux {
    private lateinit var binding : ActivityMainBinding
    private lateinit var firebaseAuth : FirebaseAuth
    private lateinit var authStateListener : FirebaseAuth.AuthStateListener
    private lateinit var adapter : ProductAdapter
    private lateinit var firestoreListener : ListenerRegistration
    private var productSelected : Product? = null
    private lateinit var firebaseAnalytics : FirebaseAnalytics
    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ it ->
        val response = IdpResponse.fromResultIntent(it.data)
        if(it.resultCode == RESULT_OK){
            val user = FirebaseAuth.getInstance().currentUser
            if(user != null){
                Toast.makeText(this,
                    getString(R.string.mssg_bienvenida),
                    Toast.LENGTH_SHORT).show()
                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN){
                    param(FirebaseAnalytics.Param.SUCCESS, 100) // 100 = login success
                    param(FirebaseAnalytics.Param.METHOD, "login")
                }
            }
        } else {
            if(response == null){
                Toast.makeText(this,
                    getString(R.string.mssg_despedida),
                    Toast.LENGTH_SHORT).show()
                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN){
                    param(FirebaseAnalytics.Param.SUCCESS, 200) // 200 = cancel
                    param(FirebaseAnalytics.Param.METHOD, "login")
                }
                finish()
            } else {
                response.error?.let{
                    if(it.errorCode == ErrorCodes.NO_NETWORK){
                        Toast.makeText(this,
                            getString(R.string.mssg_error_no_network),
                            Toast.LENGTH_SHORT).show()
                    } else{
                        Toast.makeText(this,
                            getString(R.string.mssg_error_codigo_error),
                            Toast.LENGTH_SHORT).show()
                    }
                    firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN){
                        param(FirebaseAnalytics.Param.SUCCESS, it.errorCode.toLong())
                        param(FirebaseAnalytics.Param.METHOD, "login")
                    }
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configAuth()
        configRecyclerView()
        //configFirestore()
        //configFirestoreRealtime()
        configButtons()
        configAnalytics()
    }
    private fun configButtons() {
        binding.efab.setOnClickListener {
            productSelected = null
            AddDialogFragment().show(supportFragmentManager, AddDialogFragment::class.java.simpleName)
        }
    }
    private fun configAnalytics(){
        firebaseAnalytics = Firebase.analytics
    }
    private fun configFirestoreRealtime() {
        val db = FirebaseFirestore.getInstance()
        val productRef = db.collection(Constants.COLL_PRODUCTS)
        firestoreListener = productRef.addSnapshotListener { snapshots, error ->
            if(error != null){
                Toast.makeText(this,
                    getString(R.string.mssg_error_consultar_datos),
                    Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }
            for (snapshot in snapshots!!.documentChanges){
                val product = snapshot.document.toObject(Product::class.java)
                product.id = snapshot.document.id
                when(snapshot.type){
                    DocumentChange.Type.ADDED -> adapter.addProduct(product)
                    DocumentChange.Type.MODIFIED -> adapter.updateProduct(product)
                    DocumentChange.Type.REMOVED -> adapter.deleteProduct(product)
                }
            }
        }
    }



    private fun configFirestore() {
        val db = FirebaseFirestore.getInstance()
        db.collection(Constants.COLL_PRODUCTS)
            .get()
            .addOnSuccessListener { snapshots ->
                for(document in snapshots){
                    val product = document.toObject(Product::class.java)
                    product.id = document.id
                    adapter.addProduct(product)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this,
                    getString(R.string.mssg_error_consultar_datos),
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun configRecyclerView() {
        adapter = ProductAdapter(mutableListOf(), this)
        binding.recyclerView.apply{
            layoutManager = GridLayoutManager(
                this@MainActivity,
                3,
                GridLayoutManager.HORIZONTAL,
                false)
            adapter = this@MainActivity.adapter
        }
       /* (1..20).forEach{
            val product = Product(it.toString(), "Producto $it",
                "Este producto es el $",
                "",
                it,
                it*1.11)
            adapter.addProduct(product)
        }*/
    }

    private fun configAuth(){
        firebaseAuth = FirebaseAuth.getInstance()
        authStateListener = FirebaseAuth.AuthStateListener { auth ->
            if(auth.currentUser != null){
                supportActionBar?.title = auth.currentUser?.displayName
                binding.nsvProducts.visibility = View.VISIBLE
                binding.llProgress.visibility = View.GONE
                binding.efab.show()
            } else{
                val providers = arrayListOf(
                    AuthUI.IdpConfig.EmailBuilder().build(),
                    AuthUI.IdpConfig.GoogleBuilder().build())
                resultLauncher.launch(AuthUI
                    .getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(providers)
                    .setIsSmartLockEnabled(false)
                    .build())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        firebaseAuth.addAuthStateListener(authStateListener)
        configFirestoreRealtime()
    }

    override fun onPause() {
        super.onPause()
        firebaseAuth.removeAuthStateListener(authStateListener)
        firestoreListener.remove()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.action_sign_out -> {
                AuthUI.getInstance().signOut(this)
                    .addOnSuccessListener {
                        Toast.makeText(this,
                            getString(R.string.mssg_sign_out_success),
                            Toast.LENGTH_SHORT).show()
                        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN){
                            param(FirebaseAnalytics.Param.SUCCESS, 100) // 100 = sign out success
                            param(FirebaseAnalytics.Param.METHOD, "sign_out")
                        }
                    }
                    .addOnCompleteListener {
                        if(it.isSuccessful){
                            binding.nsvProducts.visibility = View.GONE
                            binding.llProgress.visibility = View.VISIBLE
                            binding.efab.hide()
                        } else{
                            Toast.makeText(this,
                                getString(R.string.mssg_sign_out_failure),
                                Toast.LENGTH_SHORT).show()
                            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN){
                                param(FirebaseAnalytics.Param.SUCCESS, 201) // 201 = sign out error
                                param(FirebaseAnalytics.Param.METHOD, "sign_out")
                            }
                        }
                    }
            }
            R.id.action_order_history -> startActivity(Intent(this, OrderActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onClick(product: Product) {
        productSelected = product
        AddDialogFragment().show(supportFragmentManager, AddDialogFragment::class.java.simpleName)
    }

    override fun onLongClick(product: Product) {
        val db = FirebaseFirestore.getInstance()
        val productRef = db.collection("products")
        product.id?.let{id ->
            productRef.document(id)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this,
                        getString(R.string.mssg_producto_eliminado),
                        Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this,
                        getString(R.string.mssg_producto_eliminado_error),
                        Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun getProductSelected(): Product? = productSelected
}