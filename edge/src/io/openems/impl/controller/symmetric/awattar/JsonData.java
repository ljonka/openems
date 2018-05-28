package io.openems.impl.controller.symmetric.awattar;

import java.io.FileReader;
import java.io.IOException;
import java.net.URL;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonData {

	private static float minPrice = Float.MAX_VALUE;
	private static long start_timestamp = 0;
	private static long end_timestamp = 0;

	public static void jsonRead(String fileName) {
		try {

			JsonParser parser = new JsonParser();

			URL url = JsonData.class.getResource(fileName);

			Object obj;
			obj = (parser).parse(new FileReader(url.getPath())).getAsJsonObject();
			JsonObject jsonObject = (JsonObject) obj;
			JsonArray data = (JsonArray) jsonObject.get("data");

			for (JsonElement element : data) {
				JsonObject jsonelement = (JsonObject) element;

				float marketPrice = jsonelement.get("marketprice").getAsFloat();

				if (marketPrice < minPrice) {
					minPrice = marketPrice;
					start_timestamp = jsonelement.get("start_timestamp").getAsLong();
					end_timestamp = jsonelement.get("end_timestamp").getAsLong();
				}
			}
			System.out.println("Price: " + minPrice);
			System.out.println("start_timestamp: " + start_timestamp + " end_timestamp: " + end_timestamp);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static long startTimeStamp() {
		return start_timestamp;

	}
	public static long endTimeStamp() {
		return end_timestamp;

	}
}