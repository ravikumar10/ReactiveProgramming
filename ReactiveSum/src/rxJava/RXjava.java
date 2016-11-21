package rxJava;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.observables.ConnectableObservable;
import rx.schedulers.Schedulers;
import rx.subscriptions.Subscriptions;

/**
 * A demonstration of how to implement a sum that updates automatically when any of its collectors changes.
 * 
 * @author ravi kumar
 */
public class RXjava   {
	
	/**
	 * The sum is just an Observer, which subscribes to a stream created by combining 'a' and 'b', via summing.
	 * 
	 * @author ravi kumar
	 */
	public static final class ReactiveSum implements Observer<Double> {

		private CountDownLatch latch = new CountDownLatch(1);

		private double sum;
		private Subscription subscription = null;

		public ReactiveSum(Observable<Double> a, Observable<Double> b) {
			this.sum = 0;

			subscribe(a, b);
		}

		private void subscribe(Observable<Double> a, Observable<Double> b) {
			// combineLatest creates an Observable, sending notifications on changes of either of its sources.
			// This notifications are formed using a Func2.
			this.subscription = Observable.combineLatest(a, b, new Func2<Double, Double, Double>() {
				public Double call(Double a, Double b) {
					return a + b;
				}
			}).subscribeOn(Schedulers.io()).subscribe(this);
		}
		
		public void unsubscribe() {
			this.subscription.unsubscribe();
			this.latch.countDown();
		}

		public void onCompleted() {
			System.out.println("Exiting last sum was : " + this.sum);
			this.latch.countDown();
		}

		public void onError(Throwable e) {
			System.err.println("Got an error!");
			e.printStackTrace();
		}

		public void onNext(Double sum) {
			this.sum = sum;
			System.out.println("update : a + b = " + sum);
		}
		
		public CountDownLatch getLatch() {
			return latch;
		}
	}

	/**
	 * The Observable returned by this method, only reacts to values in the form
	 * <varName> = <value> or <varName> : <value>.
	 * It emits the <value>.
	 */
	public static Observable<Double> varStream(final String varName,
			Observable<String> input) {
		final Pattern pattern = Pattern.compile("^\\s*" + varName
				+ "\\s*[:|=]\\s*(-?\\d+\\.?\\d*)$");

		return input.map(new Func1<String, Matcher>() {
			public Matcher call(String str) {
				return pattern.matcher(str);
			}
		}).filter(new Func1<Matcher, Boolean>() {
			public Boolean call(Matcher matcher) {
				return matcher.matches() && matcher.group(1) != null;
			}
		}).map(new Func1<Matcher, String>() {
			public String call(Matcher matcher) {
				return matcher.group(1);
			}
		}).filter(new Func1<String, Boolean>() {
			public Boolean call(String str) {
				return str != null;
			}
		}).map(new Func1<String, Double>() {
			public Double call(String str) {
				return Double.parseDouble(str);
			}
		});
	}

	public String name() {
		return "Reactive Sum, version 1";
	}
	
	public static Observable<String> from(final Path path) {
		return Observable.<String>create(subscriber -> {
			try {
				BufferedReader reader = Files.newBufferedReader(path);
				subscriber.add(Subscriptions.create(() -> {
					try {
						reader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}));
				
				String line = null;
				while ((line = reader.readLine()) != null && !subscriber.isUnsubscribed()) {
					subscriber.onNext(line);
				}
				if (!subscriber.isUnsubscribed()) {
					subscriber.onCompleted();
				}
			} catch (IOException ioe) {
				if (!subscriber.isUnsubscribed()) {
					subscriber.onError(ioe);
				}
			}
		});
	}
	

	public static ConnectableObservable<String> from(final InputStream stream) {
		return from(new BufferedReader(new InputStreamReader(stream)));
	}

	public static Observable<String> from(final Reader reader) {
		return Observable.defer(() -> {
			return from(new BufferedReader(reader)).refCount();
		}).cache();
	}

	public static ConnectableObservable<String> from(final BufferedReader reader) {
		return Observable.create((Subscriber<? super String> subscriber) -> {
			try {
				String line;

				if (subscriber.isUnsubscribed()) {
					return;
				}

				while (!subscriber.isUnsubscribed() && (line = reader.readLine()) != null) {
					if (line.equals("exit")) {
						break;
					}

					subscriber.onNext(line);
				}
			} catch (IOException e) {
				subscriber.onError(e);
			}

			if (!subscriber.isUnsubscribed()) {
				subscriber.onCompleted();
			}
		}).publish();
	}

	public void run() {
		ConnectableObservable<String> input = from(System.in);

		Observable<Double> a = varStream("a", input);
		Observable<Double> b = varStream("b", input);

		ReactiveSum sum = new ReactiveSum(a, b);
		
		input.connect();
		
		try {
			sum.getLatch().await();
		} catch (InterruptedException e) {}
	}

	
	/**
	 * Here the input is executed on a separate thread, so we block the current one until it sends
	 * a `completed` notification.
	 */
	public static void main(String[] args) {
		System.out.println();
		System.out.println("Reacitve Sum. Type 'a: <number>' and 'b: <number>' to try it.");
		
		new RXjava().run();
	}
}