/**
 * 
 */
package de.bodden.tamiflex.reflectionview.views;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

class ReflectionViewLabelProvider extends LabelProvider {

	public String getText(Object obj) {
		return obj.toString();
	}
	public Image getImage(Object obj) {
		return ((TreeObject)obj).getImage();
	}
}