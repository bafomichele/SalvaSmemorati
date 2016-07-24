package com.example.milena.salvasmemorati;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.places.PlaceFilter;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyService extends Service {

    LocationManager mLocationManager;
    // Handler del servizio
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) { super(looper);  }
    }
    private ServiceHandler mServiceHandler;

    //  Il servizio usa una mappa che associa i tipi di task ai rispettivi tipi di places
    private Map<String, List<Integer>> typeMap;
    private Map<String, Integer> iconMap;
    //  Lista che deve contenere i task da svolgere
    protected static List<Task> taskList = MainActivity.taskList;
    //  L'istanza per le API Google deve essere globale
    protected static GoogleApiClient mGoogleApiClient = MainActivity.mGoogleApiClient;

    //  Costruttore
    public MyService() { }

    @Override
    public void onCreate() {
        System.out.println("Service Avviato");
        //  Avvia un nuovo thread, per permettere ad android di vederlo come thread in background
        HandlerThread thread = new HandlerThread("ServiceStartArguments");
        thread.start();
        //  Assegna il Looper del thread all'Handler
        Looper mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        mServiceHandler.sendMessage(msg);

        //  Prepara la mappa con i tipi di Places
        fillWithTypes();
        //  Avvia il listener della posizione
        startServices();

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
       return null;
    }

    //  Associa i codici dei tipi di Places delle API Google con le classi di task dell'app
    void fillWithTypes() {
        typeMap = new HashMap<>();
        typeMap.put("Prelevare Contanti", Arrays.asList(6, 8));
        typeMap.put("Fare Rifornimento", Collections.singletonList(41));
        typeMap.put("Parcheggiare", Collections.singletonList(70));
        typeMap.put("Mangiare", Arrays.asList(9, 15, 38, 61, 79));
        typeMap.put("Fare la Spesa", Arrays.asList(7, 26, 43, 49));
        typeMap.put("Fare Shopping", Arrays.asList(25, 83, 84, 88));
        typeMap.put("Comprare un Regalo", Arrays.asList(12, 25, 32, 37, 52, 56));
        typeMap.put("Comprare Farmaci", Collections.singletonList(72));
        typeMap.put("Spedire un oggetto", Arrays.asList(65, 77));
        typeMap.put("Cercare un Libro", Arrays.asList(12, 55));

        iconMap = new HashMap<>();
        iconMap.put("Prelevare Contanti", R.drawable.a0);
        iconMap.put("Fare Rifornimento", R.drawable.a1);
        iconMap.put("Parcheggiare", R.drawable.a2);
        iconMap.put("Mangiare", R.drawable.a3);
        iconMap.put("Fare la Spesa", R.drawable.a4);
        iconMap.put("Fare Shopping", R.drawable.a5);
        iconMap.put("Comprare un Regalo", R.drawable.a6);
        iconMap.put("Comprare Farmaci", R.drawable.a7);
        iconMap.put("Spedire un oggetto", R.drawable.a8);
        iconMap.put("Cercare un Libro",R.drawable.a9);
    }

    //  Avvia i servizi relativi alla posizione
    void startServices(){
        //  Istanzia un Listener per la posizione
        //  NOTA: LocationListener è una classe astratta, per istanziarla ne implementa i metodi astratti in una
        //  classe anonima: lancia la ricerca dei Places al cambio di posizione e non fa nulla negli altri casi.
        LocationListener mLocationListener = new LocationListener() {
            public void onLocationChanged(final Location location) { placesRadar();  }
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };
        //  Inizializza un Location Manager
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        //  Controlla se ci sono i permessi
        if (ActivityCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "Permessi mancanti", Toast.LENGTH_SHORT).show();
            return;
        }
        //  Assegna al listener la richiesta di aggiornamenti sulla posizione (minimo 10 secondi e 50 metri)
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 50f, mLocationListener);
    }

    //  Riceve la lista dei places vicini
    //  Viene chiamato quando cambia la posizione, tramite il Location Listener
    void placesRadar() {
        //  Controlla se ci sono i permessi
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "Permessi mancanti", Toast.LENGTH_SHORT).show();
            return;
        }
        //  Connette di nuovo alle API di Google
        //  Indispensabile, perché quando l'app passa in background la connessione viene chiusa
        mGoogleApiClient.connect();
        //  Recupera il buffer dei Places in zona, filtrando solo quelli aperti
        //  NOTA: Purtroppo non esiste una filtro per tipo!
        PlaceFilter filter = new PlaceFilter(true, null);
        PendingResult<PlaceLikelihoodBuffer> result = Places.PlaceDetectionApi
                .getCurrentPlace(mGoogleApiClient, filter);
        //  Definisce cosa fare quando i risultati della ricerca sono pronti, modificando
        //  il metodo onResult in una classe anonima che attende il callback
        result.setResultCallback(new ResultCallback<PlaceLikelihoodBuffer>() {
            @Override
            public void onResult(@NonNull PlaceLikelihoodBuffer placeLikelihoods) {
                //  Chiama il controllo sui tipi di Places trovati, poi rilascia il buffer
                checkPlaces(placeLikelihoods);
                placeLikelihoods.release();
            }
        });
    }

    //  Confronta i tipi dei places vicini con quelli richiesti dai task
    void checkPlaces(PlaceLikelihoodBuffer nearPlaces) {
        for (Task task : taskList) {
            //  Per ciascun task, se attivo
            if (task.getS()) {
                //  Per ognuno dei tipi di Places adatti al task(dalla mappa)
                for (Integer type : typeMap.get(task.getT())) {
                    //  Tra i Places vicini
                    for (PlaceLikelihood place : nearPlaces) {
                        System.out.println(place.getPlace().getName() + " tipi: " + place.getPlace().getPlaceTypes().toString() + " probabilità: " + place.getLikelihood());
                        //  Se è "abbastanza" vicino ed è del tipo giusto, notifica l'utente
                        //  NOTA: la likelihood non è una distanza, bensì la probabilità di trovarsi esattamente in quel Place
                        //  A seconda della densità di Places in zona, la distanza effettiva  può variare molto
                        if (place.getPlace().getPlaceTypes().contains(type) & place.getLikelihood()>0.1) {
                            notifyTask(task, place);
                        }
                    }
                }
            }
        }
    }

    // Notifica l'utente della presenza di un place idoneo a uno dei suoi task
    void notifyTask(Task task, PlaceLikelihood place) {
        //  Crea la notifica e ne imposta il contenuto
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                        .setCategory("alarm")
                        .setSmallIcon(iconMap.get(task.getT()))
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        .setContentTitle("Hai qualcosa da fare nelle vicinanze")
                        .setContentText(task.getD() + " - " + place.getPlace().getName());
        //  Controlla i permessi
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "Permessi mancanti", Toast.LENGTH_SHORT).show();
            return;
        }
        //  Riceve le posizioni (attuale e del place) e prepara un intent di Google Maps
        Location actual = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        LatLng location = place.getPlace().getLatLng();
        Intent resultIntent = new Intent (android.content.Intent.ACTION_VIEW,
                Uri.parse("http://maps.google.com/maps" +
                        "?saddr=" + actual.getLatitude() + "," + actual.getLongitude() +
                        "&daddr=" + location.latitude + "," + location.longitude));
        //  Aggiorna lo stack delle activity
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        //  Associa l'intent alla notifica e la lancia
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(0, mBuilder.build());
    }
}