package hcmute.edu.vn.befinalproject;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView userNameText, userEmailText;
    private ImageView userImage;
    private MaterialButton verifyButton, logoutButton;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Trang chủ");

        userNameText = findViewById(R.id.userNameText);
        userEmailText = findViewById(R.id.userEmailText);
        userImage = findViewById(R.id.userImage);
        verifyButton = findViewById(R.id.verifyButton);
        logoutButton = findViewById(R.id.logoutButton);

        // Set click listeners
        verifyButton.setOnClickListener(v -> startFaceVerification());
        logoutButton.setOnClickListener(v -> logout());

        // Check and request permissions
        checkAndRequestPermissions();

        // Load user data
        loadUserData();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        PERMISSION_REQUEST_CODE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, reload user data
                loadUserData();
            } else {
                Toast.makeText(this, "Cần quyền truy cập thư viện ảnh để hiển thị ảnh",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Set email
            userEmailText.setText(user.getEmail());

            // Load user data from Firestore
            db.collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            // Set name if available
                            String name = documentSnapshot.getString("name");
                            if (name != null) {
                                userNameText.setText(name);
                            }

                            // Load profile image from Base64
                            String base64Image = documentSnapshot.getString("image");
                            if (base64Image != null) {
                                try {
                                    byte[] imageBytes = Base64.decode(base64Image, Base64.DEFAULT);
                                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                                    userImage.setImageBitmap(bitmap);
                                    userImage.setVisibility(ImageView.VISIBLE);
                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this,
                                            "Lỗi khi tải ảnh: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(MainActivity.this,
                                "Lỗi khi tải thông tin người dùng: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void startFaceVerification() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String base64Image = documentSnapshot.getString("image");
                            if (base64Image != null) {
                                Intent intent = new Intent(MainActivity.this, FaceVerificationActivity.class);
                                intent.putExtra("referenceImage", base64Image);
                                startActivity(intent);
                            } else {
                                Toast.makeText(MainActivity.this,
                                        "Vui lòng đăng ký ảnh khuôn mặt trước",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    private void logout() {
        mAuth.signOut();
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            logout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}