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
    
    // ✅ CORRECCIÓN: Configuración optimizada para llamadas
    private static final int BUFFER_SIZE = 512;
    private static final int SAMPLE_RATE = 8000;
    private static final int SAMPLE_SIZE = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    
    private static String tipoLlamada = "INDIVIDUAL";
    private static String idLlamada = "";
    private static int puertoEscucha = 0;

    public static void iniciarRecepcionIndividual(int puertoEscucha) {
        System.out.println("🎧 Iniciando receptor individual en puerto: " + puertoEscucha);
        iniciarRecepcion(puertoEscucha, "INDIVIDUAL", "");
    }

    public static void iniciarRecepcionGrupal(int puertoEscucha, String idLlamadaGrupal) {
        System.out.println("🎧 Iniciando receptor grupal en puerto: " + puertoEscucha);
        iniciarRecepcion(puertoEscucha, "GRUPAL", idLlamadaGrupal);
    }

    public static void iniciarRecepcion(int puertoEscucha, String tipo, String idLlamadaEspecifica) {
        // Detener recepción anterior si está activa
        if (recibiendo) {
            System.out.println("🔄 Deteniendo recepción anterior...");
            terminarRecepcion();
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
        }

        AudioCallReceiver.tipoLlamada = tipo;
        AudioCallReceiver.idLlamada = idLlamadaEspecifica;
        AudioCallReceiver.puertoEscucha = puertoEscucha;
        
        recibiendo = true;
        socket = null;
        altavoz = null;
        paquetesRecibidos = 0;
        bytesRecibidos = 0;
        inicioRecepcion = 0;

        Thread receiverThread = new Thread(() -> {
            ejecutarRecepcionAudio();
        });
        
        receiverThread.setName("AudioReceiver-" + puertoEscucha);
        receiverThread.start();
    }

    /**
     * ✅✅✅ MÉTODO CORREGIDO - Recepción de audio funcionando
     */
    private static void ejecutarRecepcionAudio() {
        try {
            // ✅ CORRECCIÓN: Formato de audio optimizado para llamadas
            AudioFormat formato = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE,
                SAMPLE_SIZE,
                CHANNELS,
                (SAMPLE_SIZE / 8) * CHANNELS,
                SAMPLE_RATE,
                BIG_ENDIAN
            );

            System.out.println("🔊 Configurando audio - Formato: " + 
                formato.getSampleRate() + "Hz, " + formato.getSampleSizeInBits() + "bits");

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);

            // ✅ CORRECCIÓN: Verificar compatibilidad con formatos alternativos
            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("⚠️  Formato principal no soportado, probando alternativos...");
                
                // Probar formatos alternativos
                AudioFormat[] formatosAlternativos = {
                    new AudioFormat(8000.0f, 16, 1, true, false),
                    new AudioFormat(16000.0f, 16, 1, true, false),
                    new AudioFormat(44100.0f, 16, 1, true, false),
                    new AudioFormat(8000.0f, 8, 1, true, false)
                };
                
                for (AudioFormat formatoAlt : formatosAlternativos) {
                    info = new DataLine.Info(SourceDataLine.class, formatoAlt);
                    if (AudioSystem.isLineSupported(info)) {
                        formato = formatoAlt;
                        System.out.println("✅ Formato alternativo seleccionado: " + 
                            formato.getSampleRate() + "Hz");
                        break;
                    }
                }
            }

            // ✅ CORRECCIÓN: Configurar línea de audio con buffer adecuado
            altavoz = (SourceDataLine) AudioSystem.getLine(info);
            
            // Usar buffer más grande para mejor rendimiento
            int bufferSize = Math.max(BUFFER_SIZE * 4, altavoz.getBufferSize());
            altavoz.open(formato, bufferSize);
            altavoz.start();

            System.out.println("🔊 Altavoz configurado - Buffer: " + bufferSize + " bytes");

            // ✅ CORRECCIÓN: Socket con configuración optimizada
            System.out.println("🔌 Iniciando socket en puerto: " + puertoEscucha);
            socket = new DatagramSocket(puertoEscucha);
            socket.setSoTimeout(3000); // Timeout más largo
            socket.setReceiveBufferSize(65536); // Buffer de recepción más grande

            byte[] buffer = new byte[BUFFER_SIZE];
            inicioRecepcion = System.currentTimeMillis();

            System.out.println("👂 ESCUCHANDO en puerto " + puertoEscucha);
            System.out.println("💡 Escribe '10' para terminar la llamada");

            int timeoutConsecutivos = 0;
            final int MAX_TIMEOUTS = 5;

            // ✅✅✅ BUCLE PRINCIPAL CORREGIDO
            while (recibiendo) {
                try {
                    DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                    socket.receive(paquete);
                    
                    timeoutConsecutivos = 0; // Resetear contador de timeouts
                    
                    if (paquete.getLength() > 0) {
                        // ✅ CORRECCIÓN: Reproducir inmediatamente SIN procesamiento adicional
                        byte[] audioData = paquete.getData();
                        int audioLength = paquete.getLength();
                        
                        // Escribir directamente al altavoz
                        int bytesEscritos = altavoz.write(audioData, 0, audioLength);
                        
                        if (bytesEscritos > 0) {
                            paquetesRecibidos++;
                            bytesRecibidos += audioLength;
                            
                            // Log del primer paquete
                            if (paquetesRecibidos == 1) {
                                System.out.println("🎉 PRIMER PAQUETE RECIBIDO Y REPRODUCIDO! - " + 
                                    audioLength + " bytes desde " + paquete.getAddress());
                            }
                            
                            // Mostrar progreso cada 100 paquetes
                            if (paquetesRecibidos % 100 == 0) {
                                long tiempo = (System.currentTimeMillis() - inicioRecepcion) / 1000;
                                System.out.printf("📥 Recibidos: %d paquetes (%d segundos)\r", 
                                    paquetesRecibidos, tiempo);
                            }
                        } else {
                            System.err.println("⚠️  Error: No se pudieron escribir " + audioLength + " bytes al altavoz");
                        }
                    }
                    
                } catch (java.net.SocketTimeoutException e) {
                    timeoutConsecutivos++;
                    if (timeoutConsecutivos <= MAX_TIMEOUTS) {
                        if (timeoutConsecutivos % 3 == 0) {
                            System.out.printf("⏳ Esperando audio... (%d/%d timeouts)\r", 
                                timeoutConsecutivos, MAX_TIMEOUTS);
                        }
                    } else {
                        System.out.println("🔇 Sin audio recibido recientemente...");
                        timeoutConsecutivos = MAX_TIMEOUTS; // Evitar overflow
                    }
                    continue;
                } catch (Exception e) {
                    if (recibiendo) {
                        System.err.println("❌ Error en recepción: " + e.getMessage());
                        // Pequeña pausa antes de reintentar
                        try { Thread.sleep(100); } catch (InterruptedException ie) {}
                    }
                }
            }

        } catch (LineUnavailableException e) {
            System.err.println("❌ Línea de audio no disponible: " + e.getMessage());
            System.err.println("💡 Solución: Verifica que los altavoces no estén siendo usados por otra aplicación");
        } catch (SocketException e) {
            if (recibiendo) {
                System.err.println("❌ Error de socket: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("💥 ERROR crítico en AudioCallReceiver: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("🔚 Finalizando recepción de audio...");
            cerrarRecursos();
            mostrarEstadisticasFinales();
        }
    }

    /**
     * ✅ CORRECCIÓN: Cierre seguro de recursos
     */
    private static void cerrarRecursos() {
        recibiendo = false;
        
        // Cerrar altavoz
        if (altavoz != null) {
            try {
                altavoz.stop();
                altavoz.flush();
                altavoz.close();
                System.out.println("🔇 Altavoz cerrado correctamente");
            } catch (Exception e) {
                System.err.println("⚠️  Error cerrando altavoz: " + e.getMessage());
            }
            altavoz = null;
        }

        // Cerrar socket
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                System.out.println("🔌 Socket de recepción cerrado");
            } catch (Exception e) {
                System.err.println("⚠️  Error cerrando socket: " + e.getMessage());
            }
            socket = null;
        }
    }

    /**
     * ✅ CORRECCIÓN: Estadísticas mejoradas
     */
    private static void mostrarEstadisticasFinales() {
        if (inicioRecepcion == 0) {
            System.out.println("📊 No se inició recepción de audio");
            return;
        }
        
        long tiempoTotal = (System.currentTimeMillis() - inicioRecepcion) / 1000;
        if (tiempoTotal == 0) tiempoTotal = 1;
        
        System.out.println("\n📊 ESTADÍSTICAS FINALES DE RECEPCIÓN:");
        System.out.println("   Tipo: " + tipoLlamada);
        System.out.println("   Duración: " + tiempoTotal + " segundos");
        System.out.println("   Paquetes recibidos: " + paquetesRecibidos);
        System.out.println("   Datos recibidos: " + (bytesRecibidos / 1024) + " KB");
        System.out.println("   Promedio: " + (paquetesRecibidos / tiempoTotal) + " paquetes/segundo");
        System.out.println("   Estado: " + (paquetesRecibidos > 0 ? "✅ ÉXITO" : "❌ SIN DATOS"));
        
        if (paquetesRecibidos == 0) {
            System.out.println("   ⚠️  Posibles causas:");
            System.out.println("      - Firewall bloqueando puerto " + puertoEscucha);
            System.out.println("      - Problemas de red entre dispositivos");
            System.out.println("      - Formato de audio incompatible");
        }
    }

    public static void terminarRecepcion() {
        System.out.println("🛑 Solicitando terminación de recepción...");
        recibiendo = false;
        cerrarRecursos();
    }

    public static boolean isRecibiendo() {
        return recibiendo;
    }

    public static String getEstadisticas() {
        if (inicioRecepcion == 0) return "Recepción no iniciada";
        
        long tiempo = (System.currentTimeMillis() - inicioRecepcion) / 1000;
        return String.format("Recepción: %d paquetes en %d segundos", paquetesRecibidos, tiempo);
    }

    /**
     * ✅ NUEVO: Método de diagnóstico
     */
    public static void diagnosticoAudio() {
        System.out.println("\n🔍 DIAGNÓSTICO DEL SISTEMA DE AUDIO:");
        
        // Verificar formatos soportados
        AudioFormat[] formatosTest = {
            new AudioFormat(8000.0f, 16, 1, true, false),
            new AudioFormat(16000.0f, 16, 1, true, false),
            new AudioFormat(44100.0f, 16, 1, true, false)
        };
        
        for (AudioFormat formato : formatosTest) {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);
            boolean soportado = AudioSystem.isLineSupported(info);
            System.out.printf("   %5.1f kHz, %2d bits: %s%n",
                formato.getSampleRate() / 1000.0,
                formato.getSampleSizeInBits(),
                soportado ? "✅ SOPORTADO" : "❌ NO SOPORTADO");
        }
        
        // Verificar líneas de audio disponibles
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        System.out.println("   Mixers disponibles: " + mixers.length);
        for (Mixer.Info mixer : mixers) {
            System.out.println("      - " + mixer.getName());
        }
    }
}