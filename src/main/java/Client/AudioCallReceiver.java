package Client;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class AudioCallReceiver {
    private static volatile boolean recibiendo = false;
    private static DatagramSocket socket = null;
    private static SourceDataLine altavoz = null;
    
    private static long paquetesRecibidos = 0;
    private static long bytesRecibidos = 0;
    private static long inicioRecepcion = 0;
    
    private static final int BUFFER_SIZE = 512;
    private static final int SAMPLE_RATE = 8000;
    private static final int SAMPLE_SIZE = 16;
    private static final int CHANNELS = 1;
    
    private static String tipoLlamada = "INDIVIDUAL";
    private static String idLlamada = "";
    private static int puertoEscucha = 0;

     // ========== MÉTODOS PÚBLICOS SIMPLIFICADOS ==========

    /**
     * Inicia recepción para llamada individual
     */
    public static void iniciarRecepcionIndividual(int puertoEscucha) {
        System.out.println("🔊 Preparando recepción...");
        iniciarRecepcion(puertoEscucha, "INDIVIDUAL", "");
    }

    /**
     * Inicia recepción para llamada grupal
     */
    public static void iniciarRecepcionGrupal(int puertoEscucha, String idLlamadaGrupal) {
        iniciarRecepcion(puertoEscucha, "GRUPAL", idLlamadaGrupal);
    }

    /**
     * Método principal unificado para iniciar recepción
     */
    public static void iniciarRecepcion(int puertoEscucha, String tipo, String idLlamadaEspecifica) {
        // Detener recepción anterior si está activa
        if (recibiendo) {
            System.out.println("⚠️  Deteniendo recepción anterior...");
            terminarRecepcion();
            try { Thread.sleep(500); } catch (InterruptedException e) {}
        }

        AudioCallReceiver.tipoLlamada = tipo;
        AudioCallReceiver.idLlamada = idLlamadaEspecifica;
        AudioCallReceiver.puertoEscucha = puertoEscucha;
        
        recibiendo = true;
        socket = null;
        altavoz = null;
        paquetesRecibidos = 0;
        bytesRecibidos = 0;

        System.out.println("\n=== INICIANDO RECEPCIÓN " + 
                          (tipo.equals("GRUPAL") ? "GRUPAL" : "INDIVIDUAL") + " ===");
        System.out.println("Puerto: " + puertoEscucha);
        System.out.println("ID: " + (idLlamadaEspecifica.isEmpty() ? "N/A" : idLlamadaEspecifica));

        Thread receiverThread = new Thread(() -> {
            ejecutarRecepcionAudio();
        });
        
        String threadName = "AudioReceiver-" + tipo + "-" + puertoEscucha;
        receiverThread.setName(threadName);
        receiverThread.start();
    }

    // ========== MÉTODO PRIVADO DE RECEPCIÓN ==========

    private static void ejecutarRecepcionAudio() {
        try {
            AudioFormat formato = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);

            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("⚠️  Formato no soportado, probando alternativo...");
                formato = new AudioFormat(16000.0f, 16, 1, true, false);
                info = new DataLine.Info(SourceDataLine.class, formato);
                
                if (!AudioSystem.isLineSupported(info)) {
                    System.err.println("❌ No se pudo encontrar un formato de audio compatible");
                    return;
                }
            }

            // Configurar altavoz
            altavoz = (SourceDataLine) AudioSystem.getLine(info);
            altavoz.open(formato);
            altavoz.start();

            System.out.println("🔊 Altavoz configurado - Puerto: " + puertoEscucha);

            // ✅ CORRECCIÓN: Socket con bind explícito
            System.out.println("🔌 Creando socket en puerto: " + puertoEscucha);
            socket = new DatagramSocket(puertoEscucha);
            socket.setSoTimeout(2000);

            byte[] buffer = new byte[BUFFER_SIZE];
            inicioRecepcion = System.currentTimeMillis();

            System.out.println("👂 ESCUCHANDO ACTIVAMENTE en puerto " + puertoEscucha);
            System.out.println("💡 Escribe '10' en el menú para terminar");

            int timeoutCount = 0;
            
            // Bucle principal de recepción
            while (recibiendo) {
                try {
                    DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                    socket.receive(paquete);
                    
                    if (paquete.getLength() > 0) {
                        timeoutCount = 0; // Reset timeout counter
                        
                        if (paquetesRecibidos == 0) {
                            System.out.println("🎉 PRIMER PAQUETE RECIBIDO! - " + paquete.getLength() + " bytes");
                            System.out.println("   Desde: " + paquete.getAddress() + ":" + paquete.getPort());
                        }
                        
                        // ✅ Reproducir inmediatamente
                        altavoz.write(paquete.getData(), 0, paquete.getLength());
                        paquetesRecibidos++;
                        bytesRecibidos += paquete.getLength();
                        
                        if (paquetesRecibidos % 50 == 0) {
                            System.out.printf("📥 Recibidos: %d paquetes desde %s\r", 
                                paquetesRecibidos, paquete.getAddress());
                        }
                    }
                    
                } catch (java.net.SocketTimeoutException e) {
                    timeoutCount++;
                    if (timeoutCount % 10 == 0) {
                        System.out.printf("⏳ Esperando audio en puerto %d... (timeouts: %d)\r", 
                            puertoEscucha, timeoutCount);
                    }
                    if (!recibiendo) break;
                } catch (Exception e) {
                    if (recibiendo) {
                        System.err.println("⚠️  Error en recepción: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("💥 ERROR en AudioCallReceiver: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cerrarRecursos();
            mostrarEstadisticasFinales();
        }
    }

    // ========== MÉTODOS DE GESTIÓN ==========

    /**
     * Termina la recepción en curso
     */
    public static void terminarRecepcion() {
        System.out.println("🛑 Terminando recepción...");
        recibiendo = false;
        cerrarRecursos();
    }

    // ========== MÉTODOS DE INFORMACIÓN ==========

    public static boolean isRecibiendo() {
        return recibiendo;
    }

    public static String getEstadisticas() {
        if (inicioRecepcion == 0) return "No hay recepción activa";
        
        long tiempoTranscurrido = (System.currentTimeMillis() - inicioRecepcion) / 1000;
        if (tiempoTranscurrido == 0) tiempoTranscurrido = 1;
        
        return String.format(
            "Recepción %s - %d segundos - %d paquetes - %d KB",
            tipoLlamada,
            tiempoTranscurrido,
            paquetesRecibidos,
            bytesRecibidos / 1024
        );
    }

    public static String getInfoLlamada() {
        return String.format(
            "Tipo: %s | Puerto: %d | ID: %s | Activa: %s",
            tipoLlamada,
            puertoEscucha,
            idLlamada.isEmpty() ? "N/A" : idLlamada,
            recibiendo ? "Sí" : "No"
        );
    }

    // ========== MÉTODOS DE DIAGNÓSTICO ==========

    public static void diagnostico() {
        System.out.println("\n🔍 DIAGNÓSTICO DE AUDIO CALL RECEIVER:");
        System.out.println("   Estado: " + (recibiendo ? "🟢 ACTIVO" : "🔴 INACTIVO"));
        System.out.println("   Tipo llamada: " + tipoLlamada);
        System.out.println("   Puerto escucha: " + puertoEscucha);
        System.out.println("   Socket: " + (socket != null ? "🟢 CONECTADO" : "🔴 DESCONECTADO"));
        System.out.println("   Altavoz: " + (altavoz != null ? "🟢 ABIERTO" : "🔴 CERRADO"));
        System.out.println("   Paquetes recibidos: " + paquetesRecibidos);
        System.out.println("   Bytes recibidos: " + bytesRecibidos);
        
        if (recibiendo && inicioRecepcion > 0) {
            long tiempo = (System.currentTimeMillis() - inicioRecepcion) / 1000;
            System.out.println("   Tiempo activo: " + tiempo + " segundos");
        }
    }

    /**
     * Prueba rápida de los altavoces
     */
    public static void probarAltavoz() {
        System.out.println("🔊 Probando altavoz...");
        try {
            AudioFormat formato = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, true, false);
            SourceDataLine testLine = (SourceDataLine) AudioSystem.getLine(
                new DataLine.Info(SourceDataLine.class, formato));
            testLine.open(formato);
            testLine.start();
            
            // Generar tono de prueba
            byte[] tono = generarTonoPrueba(440, 1000); // 440 Hz por 1 segundo
            testLine.write(tono, 0, tono.length);
            testLine.drain();
            
            testLine.stop();
            testLine.close();
            
            System.out.println("✅ Prueba de altavoz completada");
            
        } catch (Exception e) {
            System.err.println("❌ Error en prueba de altavoz: " + e.getMessage());
        }
    }

    // ========== MÉTODOS PRIVADOS AUXILIARES ==========

    private static void mostrarEstadisticas() {
        if (paquetesRecibidos == 0) return;
        
        long tiempoTranscurrido = (System.currentTimeMillis() - inicioRecepcion) / 1000;
        if (tiempoTranscurrido == 0) return;
        
        long paquetesPorSegundo = paquetesRecibidos / tiempoTranscurrido;
        long kbps = (bytesRecibidos * 8) / (tiempoTranscurrido * 1024);
        
        System.out.println("\n📊 ESTADÍSTICAS DE RECEPCIÓN:");
        System.out.println("   Tipo: " + tipoLlamada);
        System.out.println("   Tiempo: " + tiempoTranscurrido + " segundos");
        System.out.println("   Paquetes: " + paquetesRecibidos + " (" + paquetesPorSegundo + "/s)");
        System.out.println("   Datos: " + (bytesRecibidos / 1024) + " KB (" + kbps + " kbps)");
        System.out.println("   Puerto: " + puertoEscucha);
    }

    private static void mostrarEstadisticasFinales() {
        long tiempoTotal = (System.currentTimeMillis() - inicioRecepcion) / 1000;
        if (tiempoTotal == 0) tiempoTotal = 1;
        
        System.out.println("\n📊 ESTADÍSTICAS FINALES:");
        System.out.println("   Tipo: " + tipoLlamada);
        System.out.println("   Duración: " + tiempoTotal + " segundos");
        System.out.println("   Paquetes recibidos: " + paquetesRecibidos);
        System.out.println("   Datos recibidos: " + (bytesRecibidos / 1024) + " KB");
        System.out.println("   Promedio: " + (paquetesRecibidos / tiempoTotal) + " paquetes/segundo");
        
        if (tipoLlamada.equals("GRUPAL")) {
            System.out.println("   Llamada grupal finalizada");
        } else {
            System.out.println("   Llamada individual finalizada");
        }
    }

    private static void cerrarRecursos() {
        try {
            if (altavoz != null) {
                altavoz.stop();
                altavoz.close();
                altavoz = null;
                System.out.println("🔇 Altavoz cerrado");
            }
        } catch (Exception e) {
            // ✅✅✅ CORRECCIÓN: Solo mostrar error si altavoz no es null
            if (altavoz != null) {
                System.err.println("⚠️  Error cerrando altavoz: " + e.getMessage());
            }
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
                System.out.println("🔌 Socket de recepción cerrado");
            }
        } catch (Exception e) {
            System.err.println("⚠️  Error cerrando socket: " + e.getMessage());
        }
    }

    private static byte[] generarTonoPrueba(double frecuencia, int duracionMs) {
        int sampleRate = SAMPLE_RATE;
        int sampleSize = SAMPLE_SIZE / 8;
        int framesPorBuffer = sampleRate * duracionMs / 1000;
        int bufferSize = framesPorBuffer * sampleSize * CHANNELS;
        
        byte[] buffer = new byte[bufferSize];
        double periodo = sampleRate / frecuencia;
        
        for (int i = 0; i < framesPorBuffer; i++) {
            double angulo = 2.0 * Math.PI * i / periodo;
            short muestra = (short) (Math.sin(angulo) * Short.MAX_VALUE * 0.3); // 30% volumen
            
            for (int canal = 0; canal < CHANNELS; canal++) {
                int posicion = (i * CHANNELS + canal) * sampleSize;
                for (int byteIndex = 0; byteIndex < sampleSize; byteIndex++) {
                    buffer[posicion + byteIndex] = (byte) (muestra >> (byteIndex * 8));
                }
            }
        }
        
        return buffer;
    }
}

