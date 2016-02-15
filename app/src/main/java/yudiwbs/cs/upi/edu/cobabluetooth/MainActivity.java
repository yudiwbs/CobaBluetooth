package yudiwbs.cs.upi.edu.cobabluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    //menyimpan daftar device yg terurut sesuai listview
    private ArrayList<BluetoothDevice> arrayPairedDevices = new ArrayList<>();

    //konstanta untuk req enable bluetooth, angkanya bebas
    public static final int REQUEST_ENABLE_BT=999;
    public static final int REQUEST_ENABLE_DISCOVERY=998;

    //untuk minta ijin lokasi, mulai android 6, scan bluetooth perlu ijin lokasi
    //angka bebas tapi jangan lebih dari 1 byte! (<255)
    private static final int MY_PERMISSIONS_REQUEST = 98;
    boolean isIjinLokasi = false; //sudah mendapat ijin untuk mengakses lokasi?

    BluetoothAdapter mBluetoothAdapter;

    //untuk listview
    private ArrayList<String> items = new ArrayList<>();
    ArrayAdapter adapter;

    public static final String NAME ="COBASERVER"; //service name

    //ganti UUID ini, gunakan generator spt www.uuidgenerator.net
    public UUID MY_UUID=UUID.fromString("eb5a63b9-a32b-4ca9-896c-7204f3bfc55b");


    //
    private TextView tvHasil;
    private ListView lvServer;

    ConnectedThread ct;


    /**
     *    Untuk server.
     *    Thread berisi loop sampai menerima hubungan
     */
    private  class AcceptThread extends Thread {

            private final BluetoothServerSocket mmServerSocket;
            //nanti coba tanpa final

            public AcceptThread() {
                //constructor
                //siapkan socket
                BluetoothServerSocket tmp = null;
                try {
                    tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
                } catch (IOException e) {
                    Log.e("ywlog",e.getMessage());
                    e.printStackTrace();
                }
                mmServerSocket = tmp;
            }

            public void run() {
                BluetoothSocket socket = null;
                // loop sampai socket terisi (tidak null)
                while (true) {
                    try {
                        socket = mmServerSocket.accept();
                    } catch (IOException e) {
                        Log.e("ywlog",e.getMessage());
                        break;
                    }
                    // ada koneksi yang diterima dari client
                    if (socket != null) {
                        //proses di thread terpisah
                        manageConnectedSocket(socket);

                        //karena bluetooth cuma menerima satu channel, close saja
                        try {
                            mmServerSocket.close();
                        } catch (IOException e) {
                            Log.e("ywlog",e.getMessage());
                            e.printStackTrace();
                        }
                        break;
                    }
                }
            }

            /**  Untuk mematikan server */
            public void cancel() {
                try {
                    mmServerSocket.close();
                } catch (IOException e) {
                    Log.e("ywlog",e.getMessage());
                    e.printStackTrace();
                }
            }

    }

    /*
        thread untuk client

    */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        //bluetoothdevice didapat dari proses device discovery atau dari daftar
        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // ambil bluetooth socket
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e("ywlog",e.getMessage());
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                //konek ke server
                mmSocket.connect();
            } catch (IOException connectException) {
                try {
                    mmSocket.close();
                } catch (IOException e) {
                   Log.e("ywlog",e.getMessage());
                   e.printStackTrace();
                }
                return;
            }

            //terhubung, tangani kirim data
            manageConnectedSocket(mmSocket);
        }

        /** batalkan koneksi  */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("ywlog",e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /*
       saat server dan client sudah terhubung
     */

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private String strData;

        public ConnectedThread(BluetoothSocket socket) {
            //siapkan input dan output stream
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            //ambil input dan output stream
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e("ywlog", e.getMessage());
                e.printStackTrace();
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer stream
            int bytes; // jumlah bytes yang dibaca read()

            // SERVER: mendengarkan InputStream
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    if (bytes>0) {
                        //ada data dari client!
                        //tampilkan ke textview
                        String tempS = new String(buffer);
                        strData = tempS;
                        Log.d("ywlog","data masuk ke stream! "+tempS.trim());
                        tvHasil.post(new Runnable() {
                            public void run() {
                                tvHasil.setText(strData);
                            }
                        });
                    }
                } catch (IOException e) {
                    Log.e("ywlog",e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }
        }

        /* Client: untuk menulis ke server */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e("ywlog",e.getMessage());
                e.printStackTrace();
            }
        }

        /* digunakan oleh client untuk menulis ke server*/
        public void write(String data) {
            //konversi string ke stream
            try {
                mmOutStream.write(data.getBytes());
            } catch (IOException e) {
               e.printStackTrace();
               Log.e("ywlog",e.getMessage());
            }
        }


        /* matikan connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("ywlog",e.getMessage());
                e.printStackTrace();
            }
        }
    }

    //digunakan baik oleh server maupun client
    private void manageConnectedSocket(BluetoothSocket socket) {
        //socket terhubung, mulai terima bagi server dan kirim data bagi client
        ct = new ConnectedThread(socket);
        ct.start();
    }


    //user mengklik listview, passing device
    public void client(BluetoothDevice device) {
        //start thread client
        (new ConnectThread(device)).start();
    }

    public void server()  {
        //start thread server
        (new AcceptThread()).start();
    }






    //untuk menangkap hasil method .startDiscovery
    //tertrigger untuk *setiap* device ditemukan (ACTION_FOUND)
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // ada device baru
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // ambil objek BluetoothDevice dari Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                arrayPairedDevices.add(device);
                // tulis nama dan MAC address ke ListView
                adapter.add("Baru:" + device.getName() + "\n" + device.getAddress());
                //refresh listview, JANGAN LUPA!!
                adapter.notifyDataSetChanged();
            } else
            // mulai proses discovery, untuk debug saja, memastikan proses dimulai
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Mulai proses discovery", Toast.LENGTH_LONG);
                toast.show();
            } else
            // mulai proses discovery, untuk debug saja, memastikan proses dimulai
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Proses discovery selesai", Toast.LENGTH_LONG);
                toast.show();
            }
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        String pesan;
        if (mBluetoothAdapter == null) {
            pesan = "Tidak ada bluetooth!";
            finish(); //keluar
        } else {
            pesan ="Ada bluetooth!";
        }
        Toast toast = Toast.makeText(getApplicationContext(), pesan, Toast.LENGTH_LONG);
        toast.show();

        //init listview
        ListView lv = (ListView) findViewById(R.id.listView);
        //set warna abu2, karena default font adalah putih
        lv.setBackgroundColor(Color.LTGRAY);
		adapter = new ArrayAdapter (this,android.R.layout.simple_expandable_list_item_1,items);
        lv.setAdapter(adapter);
        tampilkanPaired();

        if (!mBluetoothAdapter.isEnabled()) {
            //bluetooth dalam kondisi off
            //menampilkan dialog untuk menyalakan bluetooth (jika sedang mati)
            //setelah user memilih method onActivityResult akan ditrigger
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //mulai android 6, device discovery bluetooth perlu ijin lokasi
        if (ContextCompat.checkSelfPermission(this,
                //hati2, jika konstanta tidak cocok, tidak ada runtimeerror
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    MY_PERMISSIONS_REQUEST);
                   // MY_PERMISSIONS_REQUEST adalah konstanta, nanti digunakan di  onRequestPermissionsResult
        } else {
            //sudah diijinkan
            isIjinLokasi = true;
        }

        tvHasil = (TextView) findViewById(R.id.tvHasil);



        //siapkan onclik pada listview
        //artinya client menghubungi server
        lvServer = (ListView) findViewById(R.id.listView);
        lvServer.setClickable(true);
        lvServer.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                BluetoothDevice devicePilih = arrayPairedDevices.get(position);
                client(devicePilih);
            }
        });

    }


    //mulai android 6, scan device harus mendapatkan ijin lokasi
    //setelah muncul dialog dan user merespon (allow atau deny), method ini akan dipanggil
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

        if (requestCode == MY_PERMISSIONS_REQUEST) {
            if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                isIjinLokasi = true; //diijinkan oleh user
            }
            return;
        }
    }

    // cari paired device, dan tambah ke listview
    private void tampilkanPaired() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        //ada?
        if (pairedDevices.size() > 0) {
            // Loop semua paired devices
            for (BluetoothDevice device : pairedDevices) {
                // tambahkan ke listview
                adapter.add(device.getName() + "\n" + device.getAddress());
                //nanti kalau user mengklik listview, kita tahu device apa yg diklik
                arrayPairedDevices.add(device);
            }
        }
        //refresh listview
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            String pesan;
            if (resultCode == RESULT_OK) {
                //user menyalakan bluetooth
                pesan = "Bluetooth dinyalakan";
                tampilkanPaired();  // <-----------------------------
            } else {
                // user deny
                pesan = "Bluetooth tidak mau dinyalakan user!";
            }
            Toast toast = Toast.makeText(getApplicationContext(), pesan, Toast.LENGTH_LONG);
            toast.show();
        } else {
            //<----------------------  untuk server
            //menyalakan service discovery
            if (requestCode == REQUEST_ENABLE_DISCOVERY) {
                if (resultCode != RESULT_CANCELED) {
                    //OK, nyala sekarang
                    Log.d("ywlog","Selesai discovered, server mulai dinyalakan untuk menerima koneksi");
                    server();
                }
            }
        }
    }




    @Override
    protected void onResume() {
        // onResume juga dipanggil saat app dimulai
        // daftar broadcastreciver untuk menerima intent.
        //IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filter);
        super.onResume();
    }

    @Override
    protected void onPause() {
        // Unregister karena activity dipause.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        super.onPause();
    }

    public void klikStartDiscovery(View v) {
        //membutuhkan waktu lama (sekitar 12 detik), proses dibackground
        //hasil ditangkap dengan BroadcastReceiver


        if (!isIjinLokasi) {
            //tidak mendapat ijin lokasi. Untuk android versi dibawah 6, akan sukses tapi ...
            //di android versi 6 tidak error tapi juga tidak akan memberikan hasil --> susah didebug!!
            Toast toast = Toast.makeText(getApplicationContext(),
                    "Tidak diijinkan untuk akses lokasi. " +
                    "Proses device discovery tidak dapat dilakukan!!", Toast.LENGTH_LONG);
            toast.show();
        } else {
            //dapat ijin
            if (mBluetoothAdapter.isDiscovering()) {
                //sedang proses discovery? batalkan.
                mBluetoothAdapter.cancelDiscovery();
            }
            //mulai proses discovery
            //hasilnya akan ditangkap di BroadcastReceiver
            if (mBluetoothAdapter.startDiscovery()) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Sukses memulai proses discovery", Toast.LENGTH_LONG);
                toast.show();
            } else {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "GAGAL memulai proses discovery", Toast.LENGTH_LONG);
                toast.show();
            }
        }
    }


    public void klikKirimData(View v) {
        Log.d("ywlog","kirim data");
        ct.write("Halo dari client!");
    }

    //saat server di enable
    public void klikServer(View v) {

        // nyalakan discoverable, agar client dapat melihat (jika belum pair sebelumnya)
        // otomatis akan menyalakan bluetooth
        // hanya untuk server, kalau sebagai client tidak perlu

        Intent discoverableIntent = new
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivityForResult(discoverableIntent, REQUEST_ENABLE_DISCOVERY);
        //hasil ankan ditangkap di onActivityResult
    }


}
