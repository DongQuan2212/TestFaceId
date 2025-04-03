package hcmute.edu.vn.befinalproject;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private static final String TAG = "RegisterActivity";
    private static final int MAX_IMAGE_DIMENSION = 1024;
    private static final int JPEG_QUALITY = 80;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextInputEditText nameInput, emailInput, passwordInput, confirmPasswordInput;
    private MaterialButton selectImageButton, registerButton, loginLink;
    private ImageView faceImageView;
    private TextView faceImagePlaceholder;
    private Uri selectedImageUri;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        faceImageView.setImageURI(selectedImageUri);
                        faceImageView.setVisibility(ImageView.VISIBLE);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        selectImageButton = findViewById(R.id.selectImageButton);
        faceImageView = findViewById(R.id.faceImageView);
        faceImagePlaceholder = findViewById(R.id.faceImagePlaceholder);
        registerButton = findViewById(R.id.registerButton);
        loginLink = findViewById(R.id.loginLink);

        // Set click listeners
        selectImageButton.setOnClickListener(v -> selectImage());
        registerButton.setOnClickListener(v -> registerUser());
        loginLink.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private Bitmap compressImage(Uri uri) throws IOException {
        // Load the image
        InputStream inputStream = getContentResolver().openInputStream(uri);
        Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
        inputStream.close();

        if (originalBitmap == null) {
            throw new IOException("Không thể đọc ảnh");
        }

        // Calculate new dimensions
        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();
        float scale = 1.0f;

        if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
            if (width > height) {
                scale = (float) MAX_IMAGE_DIMENSION / width;
            } else {
                scale = (float) MAX_IMAGE_DIMENSION / height;
            }
        }

        // Create scaled bitmap
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        return Bitmap.createBitmap(originalBitmap, 0, 0, width, height, matrix, true);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
        byte[] imageBytes = outputStream.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    private void registerUser() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        // Validate inputs
        if (name.isEmpty()) {
            nameInput.setError("Vui lòng nhập họ và tên");
            return;
        }
        if (email.isEmpty()) {
            emailInput.setError("Vui lòng nhập email");
            return;
        }
        if (password.isEmpty()) {
            passwordInput.setError("Vui lòng nhập mật khẩu");
            return;
        }
        if (confirmPassword.isEmpty()) {
            confirmPasswordInput.setError("Vui lòng xác nhận mật khẩu");
            return;
        }
        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError("Mật khẩu không khớp");
            return;
        }
        if (selectedImageUri == null) {
            Toast.makeText(this, "Vui lòng chọn ảnh khuôn mặt", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        registerButton.setEnabled(false);

        // Create user account
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        try {
                            // Compress and convert image to Base64
                            Bitmap compressedBitmap = compressImage(selectedImageUri);
                            String base64Image = bitmapToBase64(compressedBitmap);

                            // Save user data to Firestore
                            String userId = mAuth.getCurrentUser().getUid();
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("name", name);
                            userData.put("email", email);
                            userData.put("image", base64Image);

                            db.collection("users").document(userId)
                                    .set(userData)
                                    .addOnSuccessListener(aVoid -> {
                                        // Navigate to MainActivity
                                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                        startActivity(intent);
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(RegisterActivity.this,
                                                "Lỗi khi lưu dữ liệu: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                        registerButton.setEnabled(true);
                                        Log.e(TAG, "Failed to save user data", e);
                                    });
                        } catch (IOException e) {
                            Toast.makeText(RegisterActivity.this,
                                    "Lỗi khi xử lý ảnh: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            registerButton.setEnabled(true);
                            Log.e(TAG, "Image processing failed", e);
                        }
                    } else {
                        // Registration failed
                        Toast.makeText(RegisterActivity.this,
                                "Đăng ký thất bại: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                        registerButton.setEnabled(true);
                    }
                });
    }
}