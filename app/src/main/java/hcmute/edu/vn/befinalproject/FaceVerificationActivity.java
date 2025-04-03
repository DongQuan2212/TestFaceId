package hcmute.edu.vn.befinalproject;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.button.MaterialButton;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class FaceVerificationActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int PICK_IMAGE_REQUEST = 1;
    private ImageView selectedImageView;
    private TextView selectedImagePlaceholder;
    private MaterialButton selectImageButton;
    private MaterialButton verifyButton;
    private String referenceImageBase64;
    private Bitmap selectedImageBitmap;
    private Bitmap referenceImageBitmap;
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        try {
                            // Convert URI to Bitmap
                            InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            inputStream.close();

                            if (bitmap != null) {
                                selectedImageBitmap = bitmap;
                                selectedImageView.setImageBitmap(bitmap);
                                selectedImageView.setVisibility(ImageView.VISIBLE);
                                selectedImagePlaceholder.setVisibility(TextView.GONE);
                            } else {
                                Toast.makeText(this, "Không thể đọc ảnh", Toast.LENGTH_SHORT).show();
                            }
                        } catch (IOException e) {
                            Toast.makeText(this, "Lỗi khi đọc ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_verification);

        // Lấy ảnh tham chiếu từ Intent
        referenceImageBase64 = getIntent().getStringExtra("referenceImage");
        if (referenceImageBase64 == null) {
            Toast.makeText(this, "Không tìm thấy ảnh tham chiếu", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Khởi tạo views
        selectedImageView = findViewById(R.id.selectedImageView);
        selectedImagePlaceholder = findViewById(R.id.selectedImagePlaceholder);
        selectImageButton = findViewById(R.id.selectImageButton);
        verifyButton = findViewById(R.id.verifyButton);

        // Tải ảnh tham chiếu
        loadReferenceImage();

        // Xử lý sự kiện chọn ảnh
        selectImageButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                openImagePicker();
            } else {
                requestPermissions();
            }
        });

        // Xử lý sự kiện xác thực
        verifyButton.setOnClickListener(v -> {
            if (selectedImageBitmap == null) {
                Toast.makeText(this, "Vui lòng chọn ảnh để xác thực", Toast.LENGTH_SHORT).show();
                return;
            }
            if (referenceImageBitmap == null) {
                Toast.makeText(this, "Đang tải ảnh tham chiếu, vui lòng đợi", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyFace(selectedImageBitmap, referenceImageBitmap);
        });
    }

    private void loadReferenceImage() {
        try {
            byte[] imageBytes = Base64.decode(referenceImageBase64, Base64.DEFAULT);
            referenceImageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            Toast.makeText(FaceVerificationActivity.this, "Đã tải xong ảnh tham chiếu", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(FaceVerificationActivity.this,
                    "Lỗi khi tải ảnh tham chiếu: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_REQUEST_CODE);
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        imagePickerLauncher.launch(intent);
    }

    private float calculateFaceSimilarity(Face face1, Face face2) {
        // Lấy các điểm mốc từ cả hai khuôn mặt
        PointF[] landmarks1 = new PointF[]{
                face1.getLandmark(FaceLandmark.LEFT_EYE).getPosition(),
                face1.getLandmark(FaceLandmark.RIGHT_EYE).getPosition(),
                face1.getLandmark(FaceLandmark.LEFT_EAR).getPosition(),
                face1.getLandmark(FaceLandmark.RIGHT_EAR).getPosition(),
                face1.getLandmark(FaceLandmark.NOSE_BASE).getPosition(),
                face1.getLandmark(FaceLandmark.MOUTH_LEFT).getPosition(),
                face1.getLandmark(FaceLandmark.MOUTH_RIGHT).getPosition(),
                face1.getLandmark(FaceLandmark.MOUTH_BOTTOM).getPosition(),
                face1.getLandmark(FaceLandmark.LEFT_CHEEK).getPosition(),
                face1.getLandmark(FaceLandmark.RIGHT_CHEEK).getPosition()
        };

        PointF[] landmarks2 = new PointF[]{
                face2.getLandmark(FaceLandmark.LEFT_EYE).getPosition(),
                face2.getLandmark(FaceLandmark.RIGHT_EYE).getPosition(),
                face2.getLandmark(FaceLandmark.LEFT_EAR).getPosition(),
                face2.getLandmark(FaceLandmark.RIGHT_EAR).getPosition(),
                face2.getLandmark(FaceLandmark.NOSE_BASE).getPosition(),
                face2.getLandmark(FaceLandmark.MOUTH_LEFT).getPosition(),
                face2.getLandmark(FaceLandmark.MOUTH_RIGHT).getPosition(),
                face2.getLandmark(FaceLandmark.MOUTH_BOTTOM).getPosition(),
                face2.getLandmark(FaceLandmark.LEFT_CHEEK).getPosition(),
                face2.getLandmark(FaceLandmark.RIGHT_CHEEK).getPosition()
        };

        // Tính toán tỷ lệ kích thước khuôn mặt
        float face1Width = face1.getBoundingBox().width();
        float face1Height = face1.getBoundingBox().height();
        float face2Width = face2.getBoundingBox().width();
        float face2Height = face2.getBoundingBox().height();

        // So sánh tỷ lệ khuôn mặt
        float aspectRatio1 = face1Width / face1Height;
        float aspectRatio2 = face2Width / face2Height;
        float aspectRatioDiff = Math.abs(aspectRatio1 - aspectRatio2);
        if (aspectRatioDiff > 0.2f) { // Nếu tỷ lệ khuôn mặt chênh lệch quá 20%
            return 0.0f;
        }

        float scaleX = face1Width / face2Width;
        float scaleY = face1Height / face2Height;
        float scale = (scaleX + scaleY) / 2;

        // Chuẩn hóa các điểm mốc
        PointF[] normalizedLandmarks2 = new PointF[landmarks2.length];
        for (int i = 0; i < landmarks2.length; i++) {
            normalizedLandmarks2[i] = new PointF(
                    landmarks2[i].x * scale,
                    landmarks2[i].y * scale
            );
        }

        // Tính tổng khoảng cách giữa các điểm mốc tương ứng
        float totalDistance = 0;
        float maxDistance = 0;
        float[] distances = new float[landmarks1.length];
        
        for (int i = 0; i < landmarks1.length; i++) {
            float dx = landmarks1[i].x - normalizedLandmarks2[i].x;
            float dy = landmarks1[i].y - normalizedLandmarks2[i].y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            distances[i] = distance;
            totalDistance += distance;
            maxDistance = Math.max(maxDistance, distance);
        }

        // Tính điểm tương đồng (0-1)
        float averageDistance = totalDistance / landmarks1.length;
        float maxAllowedDistance = Math.min(face1Width, face1Height) * 0.05f; // Giảm xuống 5% kích thước khuôn mặt
        
        // Tính độ lệch chuẩn của khoảng cách
        float variance = 0;
        for (float distance : distances) {
            float diff = distance - averageDistance;
            variance += diff * diff;
        }
        variance /= distances.length;
        float stdDev = (float) Math.sqrt(variance);
        
        // Áp dụng phạt cho độ lệch chuẩn nghiêm ngặt hơn
        float stdDevPenalty = 1 - (stdDev / (maxAllowedDistance * 0.5f));
        
        // Tính điểm tương đồng cơ bản
        float baseSimilarity = 1 - (averageDistance / maxAllowedDistance);
        
        // Áp dụng phạt cho khoảng cách tối đa nghiêm ngặt hơn
        float maxDistancePenalty = 1 - (maxDistance / (maxAllowedDistance * 1.2f));
        
        // So sánh các đặc điểm khuôn mặt
        float eyeDistance1 = calculateDistance(landmarks1[0], landmarks1[1]); // Khoảng cách giữa 2 mắt
        float eyeDistance2 = calculateDistance(normalizedLandmarks2[0], normalizedLandmarks2[1]);
        float eyeDistanceRatio = Math.min(eyeDistance1, eyeDistance2) / Math.max(eyeDistance1, eyeDistance2);
        
        float noseToMouthDistance1 = calculateDistance(landmarks1[4], landmarks1[7]); // Khoảng cách từ mũi đến miệng
        float noseToMouthDistance2 = calculateDistance(normalizedLandmarks2[4], normalizedLandmarks2[7]);
        float noseToMouthRatio = Math.min(noseToMouthDistance1, noseToMouthDistance2) / 
                                Math.max(noseToMouthDistance1, noseToMouthDistance2);

        // Tính điểm tương đồng cho các nhóm điểm mốc quan trọng với trọng số mới
        float eyeSimilarity = calculateGroupSimilarity(landmarks1, normalizedLandmarks2, 0, 1);
        float mouthSimilarity = calculateGroupSimilarity(landmarks1, normalizedLandmarks2, 5, 7);
        float faceOutlineSimilarity = calculateGroupSimilarity(landmarks1, normalizedLandmarks2, 2, 3);
        float noseSimilarity = calculateGroupSimilarity(landmarks1, normalizedLandmarks2, 4, 4);
        
        // Kết hợp các yếu tố với trọng số mới
        float similarity = baseSimilarity * maxDistancePenalty * stdDevPenalty;
        similarity = (similarity * 0.2f) + // Giảm trọng số của điểm tương đồng cơ bản
                    (eyeSimilarity * 0.25f) + // Tăng trọng số cho mắt
                    (mouthSimilarity * 0.2f) +
                    (noseSimilarity * 0.2f) + // Thêm trọng số cho mũi
                    (faceOutlineSimilarity * 0.15f);

        // Áp dụng hệ số phạt cho tỷ lệ khoảng cách
        similarity *= eyeDistanceRatio * noseToMouthRatio;

        // Đảm bảo điểm tương đồng nằm trong khoảng 0-1
        similarity = Math.max(0, Math.min(1, similarity));

        // Áp dụng ngưỡng tối thiểu nghiêm ngặt hơn
        if (similarity < 0.7f) {
            similarity *= 0.5f; // Giảm một nửa điểm cho các trường hợp dưới ngưỡng
        }

        return similarity;
    }

    private float calculateDistance(PointF p1, PointF p2) {
        float dx = p1.x - p2.x;
        float dy = p1.y - p2.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float calculateGroupSimilarity(PointF[] landmarks1, PointF[] landmarks2, int startIndex, int endIndex) {
        float totalDistance = 0;
        int count = 0;
        
        for (int i = startIndex; i <= endIndex; i++) {
            float dx = landmarks1[i].x - landmarks2[i].x;
            float dy = landmarks1[i].y - landmarks2[i].y;
            totalDistance += Math.sqrt(dx * dx + dy * dy);
            count++;
        }
        
        float averageDistance = totalDistance / count;
        float maxAllowedDistance = 30; // Giảm ngưỡng khoảng cách tối đa
        
        return Math.max(0, 1 - (averageDistance / maxAllowedDistance));
    }

    private void verifyFace(Bitmap selectedImage, Bitmap referenceImage) {
        // Tạo InputImage từ bitmap
        InputImage inputImage1 = InputImage.fromBitmap(selectedImage, 0);
        InputImage inputImage2 = InputImage.fromBitmap(referenceImage, 0);

        // Tạo FaceDetector với các tùy chọn nâng cao
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);

        // Phát hiện khuôn mặt trong cả hai ảnh
        detector.process(inputImage1)
                .addOnSuccessListener(faces1 -> {
                    if (faces1.isEmpty()) {
                        Toast.makeText(this, "Không tìm thấy khuôn mặt trong ảnh đã chọn", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    detector.process(inputImage2)
                            .addOnSuccessListener(faces2 -> {
                                if (faces2.isEmpty()) {
                                    Toast.makeText(this, "Không tìm thấy khuôn mặt trong ảnh tham chiếu", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                // Lấy khuôn mặt lớn nhất từ mỗi ảnh
                                Face face1 = faces1.get(0);
                                Face face2 = faces2.get(0);

                                for (Face face : faces1) {
                                    if (face.getBoundingBox().width() * face.getBoundingBox().height() >
                                            face1.getBoundingBox().width() * face1.getBoundingBox().height()) {
                                        face1 = face;
                                    }
                                }

                                for (Face face : faces2) {
                                    if (face.getBoundingBox().width() * face.getBoundingBox().height() >
                                            face2.getBoundingBox().width() * face2.getBoundingBox().height()) {
                                        face2 = face;
                                    }
                                }

                                // Tính điểm tương đồng
                                float similarity = calculateFaceSimilarity(face1, face2);
                                float threshold = 0.90f; // Tăng ngưỡng lên 90%

                                if (similarity >= threshold) {
                                    Toast.makeText(this, "Xác thực thành công! Độ tương đồng: " +
                                            String.format("%.1f%%", similarity * 100), Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(this, "Xác thực thất bại! Độ tương đồng: " +
                                            String.format("%.1f%%", similarity * 100), Toast.LENGTH_LONG).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Lỗi khi xử lý ảnh tham chiếu: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi khi xử lý ảnh đã chọn: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                Toast.makeText(this, "Cần quyền truy cập thư viện ảnh để chọn ảnh", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                try {
                    // Lấy quyền truy cập URI
                    getContentResolver().takePersistableUriPermission(selectedImageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    
                    // Chuyển đổi URI thành Bitmap
                    InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                    selectedImageBitmap = BitmapFactory.decodeStream(inputStream);
                    
                    // Hiển thị ảnh đã chọn
                    selectedImageView.setImageBitmap(selectedImageBitmap);
                    selectedImageView.setVisibility(ImageView.VISIBLE);
                    selectedImagePlaceholder.setVisibility(TextView.GONE);
                    
                    Toast.makeText(this, "Đã chọn ảnh thành công", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Lỗi khi tải ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
} 