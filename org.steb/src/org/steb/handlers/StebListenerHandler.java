package org.steb.handlers;

import java.io.IOException;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;
import org.steb.Activator;

public class StebListenerHandler extends AbstractHandler implements IElementUpdater {
	
	private UIElement element;

	public StebListenerHandler() {
	}

	/**
	 * the command has been executed, so extract extract the needed information
	 * from the application context.
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Command command = event.getCommand();		
	    boolean oldVal = HandlerUtil.toggleCommandState(command);
	    boolean newVal = !oldVal;

		if (oldVal == false && newVal == true) {
			try {
				Activator.getDefault().startupListener();
			} catch (IOException e) {
				return HandlerUtil.toggleCommandState(command);				
			}
		} else if (oldVal == true && newVal == false) {
			Activator.getDefault().shutdownListener();
		}
	    
		return new Boolean(newVal);
	}

	@Override
	public void updateElement(UIElement element, Map parameters) {
		this.element = element;		
	}
}
