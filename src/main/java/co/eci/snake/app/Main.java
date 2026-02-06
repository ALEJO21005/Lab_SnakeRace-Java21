package co.eci.snake.app;

import co.eci.snake.ui.legacy.SnakeApp;

public final class Main {
  private Main() {}
  
  // CONFIGURACIONES DE PRUEBA - DESCOMENTAR UNA
  
  // Prueba normal (2 serpientes)
  private static final int NUM_SNAKES = 2;
  
  // Prueba básica (20 serpientes)
  // private static final int NUM_SNAKES = 20;
  
  // Prueba media (30 serpientes)
  // private static final int NUM_SNAKES = 30;
  
  // Prueba de carga alta (50 serpientes)
  // private static final int NUM_SNAKES = 50;
  
  // Prueba extrema (100 serpientes)
  // private static final int NUM_SNAKES = 100;
  
  public static void main(String[] args) {
    // Si se pasa -Dsnakes=N desde consola, tiene prioridad
    // Si no, usa la constante NUM_SNAKES configurada arriba
    int snakeCount = Integer.getInteger("snakes", NUM_SNAKES);
    SnakeApp.launch(snakeCount);
  }
}
