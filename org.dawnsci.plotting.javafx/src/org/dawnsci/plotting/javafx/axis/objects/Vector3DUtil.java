package org.dawnsci.plotting.javafx.axis.objects;

import javafx.collections.ObservableList;
import javafx.geometry.Point3D;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Transform;


// class to hold basic vector calculations that I need throughout the project
public class Vector3DUtil
{
		
	
	/*
	 * static functions
	 * 
	 */
	
	public static Rotate rotateVector(Point3D u , Point3D v)
	{
		
		double angle = u.angle(v);
		
		// find the normal of the vectors
		Point3D normal = u.crossProduct(v);
		
		// create the rotation via the normal and angle
		Rotate returnRotate = new Rotate();
		returnRotate.setAxis(new Point3D(normal.getX(), normal.getY(), normal.getZ()));
		returnRotate.setAngle(angle);
		
		return returnRotate;
			
	}
		
	/*
	 * take 4 vectors - 2 groups - 2 u, 2 v 
	 * will find the rotation to align u with v in a clock wise direction
	 */
	
	public static Rotate alignClockWiseRotation( Point3D startingVector1, Point3D startingVector2, Point3D finalVector1, Point3D finalVector2)
	{
		// this should be renamed
		// find the normals of each vector in sequence 1-1 -> 1-2 -> 2-2
		Point3D startingNormal = startingVector2.crossProduct(startingVector1);
		Point3D finalNormal = finalVector1.crossProduct(startingVector2);
		Point3D startingEndFinalEndNormal = finalVector2.crossProduct(finalVector1);
		
		Point3D averageNormal = startingNormal.add(finalNormal).add(startingEndFinalEndNormal);
				
		Point3D projectedstartingVector1 = getVectorPlaneProjection(averageNormal, startingVector1);
		Point3D projectedstartingfinalVector1 = getVectorPlaneProjection(averageNormal, finalVector1);
		double angleOfRotation = projectedstartingVector1.angle(projectedstartingfinalVector1);
		
		
		Rotate clockWiseRotation = new Rotate(angleOfRotation, averageNormal);
				
		return clockWiseRotation;
	}
	
	public static Point3D getVectorPlaneProjection(Point3D planeNormal, Point3D vector)
	{
		double dot = vector.dotProduct(planeNormal);
		double magnitude = planeNormal.magnitude();
		
		Point3D projectedNormalVector = planeNormal.multiply(dot/Math.pow(magnitude,2));
		
		Point3D projectedVector = vector.subtract(projectedNormalVector);
				
		return projectedVector;
	}
	
	public static double getScaleAcrossProjectedVector(Point3D vector, Point3D vectorToScale)
	{
		double dot = vectorToScale.dotProduct(vector);
		double magnitude = vector.magnitude();
		
		double returnDouble = vector.multiply(dot/Math.pow(magnitude,2)).magnitude() ;
		
		if (dot <= 0)
		{
			returnDouble *= -1;
		}
		
		return returnDouble;
	}
		
	public static Point3D exclusiveTransforms(ObservableList<Transform> tranformsList, Point3D point, Class<?> tranformClassType)
	{
		
		Point3D direction = point;
		for (Transform currentTransform : tranformsList)
		{
			if (tranformClassType.isInstance(currentTransform))
			{
				direction = currentTransform.transform(direction);
			}
			else if (tranformClassType == Transform.class)
			{
				direction = currentTransform.transform(direction);
			}
		}
		return direction;
	}
	
}
















