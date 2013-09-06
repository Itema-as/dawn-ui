package org.dawnsci.slicing.api.system;

import uk.ac.diamond.scisoft.analysis.dataset.ILazyDataset;

/**
 * This class represents information which configures a slice component
 * to 
 * 
 * @author fcp94556
 *
 */
public class SliceSource {

	private ILazyDataset lazySet; 
	private String       dataName; 
	private String       filePath;
	private boolean      isExpression;
	
	public SliceSource() {
		
	}
	public SliceSource(ILazyDataset l, String name, String path, boolean isExpr) {
		setLazySet(l);
		setDataName(name);
		setFilePath(path);
		setExpression(isExpr);
	}
	
	public ILazyDataset getLazySet() {
		return lazySet;
	}
	public void setLazySet(ILazyDataset lazySet) {
		this.lazySet = lazySet;
	}
	public String getDataName() {
		return dataName;
	}
	public void setDataName(String dataName) {
		this.dataName = dataName;
	}
	public String getFilePath() {
		return filePath;
	}
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}
	public boolean isExpression() {
		return isExpression;
	}
	public void setExpression(boolean isExpression) {
		this.isExpression = isExpression;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((dataName == null) ? 0 : dataName.hashCode());
		result = prime * result
				+ ((filePath == null) ? 0 : filePath.hashCode());
		result = prime * result + (isExpression ? 1231 : 1237);
		result = prime * result + ((lazySet == null) ? 0 : lazySet.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SliceSource other = (SliceSource) obj;
		if (dataName == null) {
			if (other.dataName != null)
				return false;
		} else if (!dataName.equals(other.dataName))
			return false;
		if (filePath == null) {
			if (other.filePath != null)
				return false;
		} else if (!filePath.equals(other.filePath))
			return false;
		if (isExpression != other.isExpression)
			return false;
		if (lazySet == null) {
			if (other.lazySet != null)
				return false;
		} else if (!lazySet.equals(other.lazySet))
			return false;
		return true;
	}
	
}
