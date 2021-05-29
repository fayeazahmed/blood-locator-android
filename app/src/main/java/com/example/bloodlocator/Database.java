package com.example.bloodlocator;

import android.annotation.SuppressLint;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Map;
import java.util.Objects;

interface dataReadCallBack {
    void onDocReceived(List<DocumentSnapshot> docs);
}

interface dataInsertCallBack {
    void onInsertAttempt(boolean success, DocumentSnapshot doc);
}

interface dataDeleteCallBack {
    void onDeleteAttempt(boolean success);
}

public class Database {
    @SuppressLint("StaticFieldLeak")
    private static FirebaseFirestore db;
    private static final String COLLECTION_NAME = "donor";

    public static void getDonors(String bloodGroup, dataReadCallBack callback) {
        if(db == null) {
            db = FirebaseFirestore.getInstance();
        }

        db.collection(COLLECTION_NAME)
                .whereEqualTo("bloodGroup", bloodGroup)
                .get()
                .addOnCompleteListener(task -> callback.onDocReceived(Objects.requireNonNull(task.getResult()).getDocuments()));
    }

    public static void addDonor(Map<String, Object> data, dataInsertCallBack callback) {
        db.collection(COLLECTION_NAME)
                .add(data)
                .addOnSuccessListener(documentReference -> documentReference.get().addOnCompleteListener(task -> callback.onInsertAttempt(true, task.getResult())))
                .addOnFailureListener(e -> callback.onInsertAttempt(false, null));
    }

    public static void deleteDonor(String documentId, dataDeleteCallBack callBack) {
        db.collection(COLLECTION_NAME)
                .document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> callBack.onDeleteAttempt(true))
                .addOnFailureListener(e -> callBack.onDeleteAttempt(false));
    }
}
