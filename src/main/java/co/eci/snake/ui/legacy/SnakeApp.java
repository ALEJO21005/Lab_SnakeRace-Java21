package co.eci.snake.ui.legacy;

import co.eci.snake.concurrency.PauseController;
import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class SnakeApp extends JFrame {

  private final Board board;
  private final GamePanel gamePanel;
  private final JButton actionButton;
  private final GameClock clock;
  private final java.util.List<Snake> snakes = new java.util.ArrayList<>();
  private final PauseController pauseController;
  private final java.util.Map<Integer, Integer> maxLengths = new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<Integer, Long> deathTimes = new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Set<Integer> deadSnakes = java.util.concurrent.ConcurrentHashMap.newKeySet();

  public SnakeApp(int numSnakes) {
    super("The Snake Race");
    this.board = new Board(35, 28);

    int N = numSnakes;
    this.pauseController = new PauseController(N);
    
    for (int i = 0; i < N; i++) {
      int x = 2 + (i * 3) % board.width();
      int y = 2 + (i * 2) % board.height();
      var dir = Direction.values()[i % Direction.values().length];
      snakes.add(Snake.of(x, y, dir));
    }

    this.gamePanel = new GamePanel(board, () -> snakes, () -> this.getStatsMessage());
    this.actionButton = new JButton("Action");

    setLayout(new BorderLayout());
    add(gamePanel, BorderLayout.CENTER);
    add(actionButton, BorderLayout.SOUTH);

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null);

    this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(gamePanel::repaint));

    var exec = Executors.newVirtualThreadPerTaskExecutor();
    for (int i = 0; i < snakes.size(); i++) {
      final int snakeId = i;
      final Snake snake = snakes.get(i);
      exec.submit(() -> {
        try {
          int collisionCount = 0;
          int turboTicks = 0;
          final int baseSleep = 80;
          final int turboSleep = 40;
          
          while (!Thread.currentThread().isInterrupted()) {
            pauseController.checkPause();
            
            // Actualizar longitud máxima
            int currentLength = snake.snapshot().size();
            maxLengths.merge(snakeId, currentLength, Math::max);
            
            var res = board.step(snake);
            
            if (res == Board.MoveResult.HIT_OBSTACLE) {
              collisionCount++;
              if (collisionCount >= 3) {
                deadSnakes.add(snakeId);
                deathTimes.put(snakeId, System.currentTimeMillis());
                break;
              }
              snake.turn(Direction.values()[(int)(Math.random() * 4)]);
              
            } else if (res == Board.MoveResult.ATE_TURBO) {
              collisionCount = 0;
              turboTicks = 100;
              
            } else if (res == Board.MoveResult.TELEPORTED) {
              collisionCount = 0;
              
            } else {
              collisionCount = 0;
            }
            
            // Usar velocidad turbo si está activo
            int sleepTime = (turboTicks > 0) ? turboSleep : baseSleep;
            if (turboTicks > 0) turboTicks--;
            
            Thread.sleep(sleepTime);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
    }

    actionButton.addActionListener((ActionEvent e) -> togglePause());

    gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "pause");
    gamePanel.getActionMap().put("pause", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        togglePause();
      }
    });

    var player = snakes.get(0);
    InputMap im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap am = gamePanel.getActionMap();
    im.put(KeyStroke.getKeyStroke("LEFT"), "left");
    im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
    im.put(KeyStroke.getKeyStroke("UP"), "up");
    im.put(KeyStroke.getKeyStroke("DOWN"), "down");
    am.put("left", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.LEFT);
      }
    });
    am.put("right", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.RIGHT);
      }
    });
    am.put("up", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.UP);
      }
    });
    am.put("down", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.DOWN);
      }
    });

    if (snakes.size() > 1) {
      var p2 = snakes.get(1);
      im.put(KeyStroke.getKeyStroke('A'), "p2-left");
      im.put(KeyStroke.getKeyStroke('D'), "p2-right");
      im.put(KeyStroke.getKeyStroke('W'), "p2-up");
      im.put(KeyStroke.getKeyStroke('S'), "p2-down");
      am.put("p2-left", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.LEFT);
        }
      });
      am.put("p2-right", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.RIGHT);
        }
      });
      am.put("p2-up", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.UP);
        }
      });
      am.put("p2-down", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.DOWN);
        }
      });
    }

    setVisible(true);
  }

  private void togglePause() {
    String currentText = actionButton.getText();
    
    if ("Action".equals(currentText)) {
      actionButton.setText("Pausar");
      clock.start();
      pauseController.resume();
      
    } else if ("Pausar".equals(currentText)) {
      actionButton.setText("Pausando...");
      actionButton.setEnabled(false);
      
      new Thread(() -> {
        clock.pause();
        boolean success = pauseController.requestPause();
        
        SwingUtilities.invokeLater(() -> {
          if (success) {
            actionButton.setText("Reanudar");
            actionButton.setEnabled(true);
            gamePanel.repaint();
          } else {
            // Si falla, forzar pausa de todos modos
            actionButton.setText("Reanudar");
            actionButton.setEnabled(true);
            gamePanel.repaint();
          }
        });
      }).start();
      
    } else if ("Reanudar".equals(currentText) || currentText.startsWith("Error")) {
      actionButton.setText("Pausar");
      pauseController.resume();
      clock.resume();
    }
  }

  private String getStatsMessage() {
    if (!pauseController.isPaused()) {
      return null;
    }
    Snake longest = null;
    int maxLength = 0;
    int longestIndex = -1;
    
    for (int i = 0; i < snakes.size(); i++) {
      if (!deadSnakes.contains(i)) {
        int length = snakes.get(i).snapshot().size();
        if (length > maxLength) {
          maxLength = length;
          longest = snakes.get(i);
          longestIndex = i;
        }
      }
    }

    Integer firstDead = null;
    long earliestDeath = Long.MAX_VALUE;
    
    for (Integer snakeId : deadSnakes) {
      Long deathTime = deathTimes.get(snakeId);
      if (deathTime != null && deathTime < earliestDeath) {
        earliestDeath = deathTime;
        firstDead = snakeId;
      }
    }

    StringBuilder msg = new StringBuilder("=== JUEGO PAUSADO ===\n\n");

    if (longest != null) {
      msg.append(String.format("Serpiente mas larga viva: #%d (Longitud: %d)\n",
          longestIndex + 1, maxLength));
    } else {
      msg.append("No hay serpientes vivas\n");
    }

    if (firstDead != null) {
      Integer maxLen = maxLengths.get(firstDead);
      msg.append(String.format("\nPrimera en morir: Serpiente #%d (Longitud max: %d)\n",
          firstDead + 1, maxLen != null ? maxLen : 0));
    } else {
      msg.append("\nNinguna serpiente ha muerto\n");
    }

    long aliveCount = snakes.size() - deadSnakes.size();
    msg.append(String.format("\nVivas: %d | Muertas: %d", aliveCount, deadSnakes.size()));

    return msg.toString();
  }

  public static final class GamePanel extends JPanel {
    private final Board board;
    private final Supplier snakesSupplier;
    private final StatsSupplier statsSupplier;
    private final int cell = 20;

    @FunctionalInterface
    public interface Supplier {
      List<Snake> get();
    }

    @FunctionalInterface
    public interface StatsSupplier {
      String get();
    }

    public GamePanel(Board board, Supplier snakesSupplier, StatsSupplier statsSupplier) {
      this.board = board;
      this.snakesSupplier = snakesSupplier;
      this.statsSupplier = statsSupplier;
      setPreferredSize(new Dimension(board.width() * cell + 1, board.height() * cell + 40));
      setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g2.setColor(new Color(220, 220, 220));
      for (int x = 0; x <= board.width(); x++)
        g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      for (int y = 0; y <= board.height(); y++)
        g2.drawLine(0, y * cell, board.width() * cell, y * cell);

      // Obstáculos
      g2.setColor(new Color(255, 102, 0));
      for (var p : board.obstacles()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillRect(x + 2, y + 2, cell - 4, cell - 4);
        g2.setColor(Color.RED);
        g2.drawLine(x + 4, y + 4, x + cell - 6, y + 4);
        g2.drawLine(x + 4, y + 8, x + cell - 6, y + 8);
        g2.drawLine(x + 4, y + 12, x + cell - 6, y + 12);
        g2.setColor(new Color(255, 102, 0));
      }

      // Ratones
      g2.setColor(Color.BLACK);
      for (var p : board.mice()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillOval(x + 4, y + 4, cell - 8, cell - 8);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 8, y + 8, cell - 16, cell - 16);
        g2.setColor(Color.BLACK);
      }

      // Teleports (flechas rojas)
      Map<Position, Position> tp = board.teleports();
      g2.setColor(Color.RED);
      for (var entry : tp.entrySet()) {
        Position from = entry.getKey();
        int x = from.x() * cell, y = from.y() * cell;
        int[] xs = { x + 4, x + cell - 4, x + cell - 10, x + cell - 10, x + 4 };
        int[] ys = { y + cell / 2, y + cell / 2, y + 4, y + cell - 4, y + cell / 2 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Turbo (rayos)
      g2.setColor(Color.BLACK);
      for (var p : board.turbo()) {
        int x = p.x() * cell, y = p.y() * cell;
        int[] xs = { x + 8, x + 12, x + 10, x + 14, x + 6, x + 10 };
        int[] ys = { y + 2, y + 2, y + 8, y + 8, y + 16, y + 10 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Serpientes
      var snakes = snakesSupplier.get();
      int idx = 0;
      for (Snake s : snakes) {
        var body = s.snapshot().toArray(new Position[0]);
        for (int i = 0; i < body.length; i++) {
          var p = body[i];
          Color base = (idx == 0) ? new Color(0, 170, 0) : new Color(0, 160, 180);
          int shade = Math.max(0, 40 - i * 4);
          g2.setColor(new Color(
              Math.min(255, base.getRed() + shade),
              Math.min(255, base.getGreen() + shade),
              Math.min(255, base.getBlue() + shade)));
          g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
        }
        idx++;
      }

      String statsMessage = statsSupplier.get();
      if (statsMessage != null) {
        g2.setColor(new Color(0, 0, 0, 180));
        int boxWidth = 400;
        int boxHeight = 200;
        int boxX = (getWidth() - boxWidth) / 2;
        int boxY = (getHeight() - boxHeight) / 2;
        g2.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 20, 20);

        g2.setColor(new Color(255, 215, 0));
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 20, 20);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        
        int textY = boxY + 35;
        for (String line : statsMessage.split("\n")) {
          FontMetrics fm = g2.getFontMetrics();
          int textX = boxX + (boxWidth - fm.stringWidth(line)) / 2;
          g2.drawString(line, textX, textY);
          textY += 22;
        }
      }

      g2.dispose();
    }
  }

  public static void launch() {
    launch(Integer.getInteger("snakes", 2));
  }
  
  public static void launch(int numSnakes) {
    SwingUtilities.invokeLater(() -> new SnakeApp(numSnakes).setVisible(true));
  }
}
