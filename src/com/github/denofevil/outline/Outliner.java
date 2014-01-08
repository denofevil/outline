package com.github.denofevil.outline;

import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;

/**
 * @author Dennis.Ushakov
 */
public class Outliner implements ProjectComponent, Disposable {
  private static final int OUTLINE_WIDTH = 150;
  private static final double SCALE = 0.10;

  private final Project myProject;

  public Outliner(Project project) {
    myProject = project;
    Disposer.register(project, this);
  }

  @Override
  public void projectOpened() {
    final MessageBusConnection connect = myProject.getMessageBus().connect();
    connect.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        for (FileEditor newEditor : source.getAllEditors(file)) {
          if (newEditor instanceof TextEditor) {
            final EditorImpl editor = (EditorImpl) ((TextEditor) newEditor).getEditor();
            editor.getScrollPane().setViewportBorder(new OutlineBorder(editor));
          }
        }
      }
    });
  }

  @Override
  public void projectClosed() {

  }

  @Override
  public void initComponent() {

  }

  @Override
  public void disposeComponent() {

  }

  @NotNull
  @Override
  public String getComponentName() {
    return "Outliner";
  }

  @Override
  public void dispose() {

  }

  private class OutlineBorder extends EmptyBorder {
    private static final int TRIM = 80;
    private final EditorImpl myEditor;
    private final Alarm myInvalidationAlarm;
    private Image myCachedImage;

    public OutlineBorder(EditorImpl editor) {
      super(0, 0, 0, Outliner.OUTLINE_WIDTH - 15);
      myEditor = editor;
      myInvalidationAlarm = new Alarm(myEditor.getDisposable());

      myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        public void documentChanged(DocumentEvent documentEvent) {
          invalidateImage();
        }
      }, editor.getDisposable());
      myEditor.getComponent().addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent componentEvent) {
          invalidateImage();
        }
      });
    }

    private void invalidateImage() {
      myInvalidationAlarm.cancelAllRequests();
      myInvalidationAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          myCachedImage = null;
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              myEditor.getComponent().repaint();
            }
          });
        }
      }, 100);
    }

    @Override
    public void paintBorder(Component component, Graphics graphics, int i, int i2, int i3, int i4) {
      super.paintBorder(component, graphics, i, i2, i3, i4);
      if (myCachedImage == null) {
        myCachedImage = createBufferedImage();
      }
      graphics.drawImage(myCachedImage, component.getX() + component.getWidth() - OUTLINE_WIDTH, component.getY(),
          OUTLINE_WIDTH, myCachedImage.getHeight(null), null);
    }

    private Image createBufferedImage() {
      final EditorFragmentComponent fragment = EditorFragmentComponent.createEditorFragmentComponent(myEditor, 0, myEditor.getDocument().getLineCount(), false, false);
      final Dimension size = fragment.getPreferredSize();
      final int maxWidth = EditorUtil.charWidth('w', Font.PLAIN, myEditor) * TRIM;
      final int editorHeight = myEditor.getComponent().getHeight();

      final double xScale = 1. * OUTLINE_WIDTH / maxWidth;
      final double yScale = Math.min(xScale / 1.5, 1. * editorHeight / size.height);

      final int width = Math.min(maxWidth, size.width);
      final int height = Math.max((int) (editorHeight / yScale), size.height);

      fragment.setSize(new Dimension(width, height));
      fragment.doLayout();
      final BufferedImage image = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_RGB);
      final Graphics graphics = image.getGraphics();
      UISettings.setupAntialiasing(graphics);
      fragment.printAll(graphics);
      graphics.dispose();

      return image.getScaledInstance((int)(width * xScale), (int)(height * yScale), Image.SCALE_SMOOTH);
    }
  }
}
